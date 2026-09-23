package com.macrotracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.update.AppReleaseNotes
import com.macrotracker.data.update.AppUpdateInfo
import com.macrotracker.data.update.AppUpdateInstaller
import com.macrotracker.data.update.AppUpdateNotifier
import com.macrotracker.data.update.AppUpdateRepository
import com.macrotracker.data.update.AppUpdateUiState
import com.macrotracker.data.update.WhatsNewInfo
import com.macrotracker.data.update.info
import com.macrotracker.data.update.inProgress
import com.macrotracker.data.update.updateAvailable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The in-app updater as the screens see it: what GitHub has, and where this phone is with
 * installing it. The install itself belongs to [AppUpdateInstaller], which outlives this
 * view model, so the sheet always shows the step the update is actually on.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val installer: AppUpdateInstaller,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "AppUpdateVM"
    }

    /** What the last look at GitHub found; the install phase is laid over it. */
    private val check = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    private val canInstall = MutableStateFlow(repository.canInstallPackages())

    val state: StateFlow<AppUpdateUiState> = combine(check, installer.phase, canInstall) { found, phase, allowed ->
        when (phase) {
            is AppUpdateInstaller.Phase.Downloading ->
                AppUpdateUiState.Downloading(phase.info, phase.progress, phase.downloaded, phase.total)
            is AppUpdateInstaller.Phase.Installing ->
                AppUpdateUiState.Installing(phase.info, phase.awaitingConfirmation)
            is AppUpdateInstaller.Phase.Ready ->
                AppUpdateUiState.ReadyToInstall(phase.info, phase.apkPath, phase.note)
            is AppUpdateInstaller.Phase.Failed ->
                AppUpdateUiState.Error(phase.message, phase.info)
            AppUpdateInstaller.Phase.Idle ->
                if (found is AppUpdateUiState.Available && !allowed) AppUpdateUiState.NeedsPermission(found.info) else found
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUpdateUiState.Idle)

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _whatsNew = MutableStateFlow<WhatsNewInfo?>(null)
    val whatsNew: StateFlow<WhatsNewInfo?> = _whatsNew.asStateFlow()

    private val _releaseNotes = MutableStateFlow<List<AppReleaseNotes>>(emptyList())
    val releaseNotes: StateFlow<List<AppReleaseNotes>> = _releaseNotes.asStateFlow()

    private val _releaseNotesLoading = MutableStateFlow(false)
    val releaseNotesLoading: StateFlow<Boolean> = _releaseNotesLoading.asStateFlow()

    private val _notifyEnabled = MutableStateFlow(repository.notifyEnabled())
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled.asStateFlow()

    /** True when a newer APK is available (drives Settings tab badge). */
    val updateAvailable: StateFlow<Boolean> = state
        .map { it.updateAvailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Android's prompt closed, or the install failed, while the sheet was away: bring
        // it back with Install on it rather than leave the update stranded half done.
        viewModelScope.launch {
            installer.phase.collect { phase ->
                if (phase is AppUpdateInstaller.Phase.Ready || phase is AppUpdateInstaller.Phase.Failed) {
                    _showDialog.value = true
                }
            }
        }
    }

    val currentVersionName: String get() = repository.currentVersionName()
    val currentVersionCode: Int get() = repository.currentVersionCode()

    private var checkJob: Job? = null
    private var listenJob: Job? = null
    private var releaseNotesJob: Job? = null

    /** Set when Update was tapped before Android allowed installs; picked up on return. */
    private var pendingAfterPermission: AppUpdateInfo? = null
    private var listeningStarted = false
    private var postUpdateHandled = false

    /**
     * Start foreground listening: immediate check, then poll GitHub every
     * [AppUpdateRepository.FOREGROUND_POLL_INTERVAL_MS] so a newly published
     * release prompts in-app quickly.
     */
    fun startListening() {
        if (listeningStarted) return
        listeningStarted = true
        checkForUpdate(showDialogIfAvailable = true, forceNetwork = true, quiet = true)
        loadReleaseNotes()
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            while (isActive) {
                delay(AppUpdateRepository.FOREGROUND_POLL_INTERVAL_MS)
                checkForUpdate(showDialogIfAvailable = true, forceNetwork = true, quiet = true)
            }
        }
    }

    fun willShowWhatsNew(forceFromIntent: Boolean): Boolean =
        repository.willShowWhatsNew(forceFromIntent)

    /**
     * Call once after Compose is up. Handles post-install relaunch / first launch
     * onto a newer versionCode and prepares the What's New sheet.
     *
     * @return true when a What's New sheet was queued.
     */
    fun handlePostUpdateLaunch(forceFromIntent: Boolean): Boolean {
        if (postUpdateHandled && !forceFromIntent) return _whatsNew.value != null
        postUpdateHandled = true
        val info = repository.consumePostUpdateWhatsNew(forceFromIntent) ?: return false
        installer.reset()
        check.value = AppUpdateUiState.UpToDate
        _whatsNew.value = info
        // Prefer live notes from GitHub when the offline cache was a placeholder.
        viewModelScope.launch {
            try {
                val releases = repository.listReleaseNotes()
                _releaseNotes.value = releases
                _whatsNew.value = repository.enrichWhatsNewFromReleases(info, releases)
            } catch (e: Exception) {
                Log.w(TAG, "Could not enrich What's New from GitHub", e)
            }
        }
        return true
    }

    fun dismissWhatsNew() {
        val code = _whatsNew.value?.versionCode ?: currentVersionCode
        repository.markWhatsNewSeen(code)
        _whatsNew.value = null
    }

    /**
     * Back in the foreground: Android's install permission may have just been granted, in
     * which case the update that was waiting on it starts now. Then a throttled re-check.
     */
    fun checkOnResume() {
        canInstall.value = repository.canInstallPackages()
        pendingAfterPermission?.let { pending ->
            if (canInstall.value) {
                pendingAfterPermission = null
                _showDialog.value = true
                installer.start(pending)
            }
        }
        checkForUpdate(showDialogIfAvailable = true, forceNetwork = false, quiet = true)
    }

    /** Manual check from Settings — always hits the network and clears soft snooze. */
    fun checkFromSettings() {
        repository.clearDismissed()
        checkForUpdate(showDialogIfAvailable = true, forceNetwork = true, quiet = false)
        loadReleaseNotes(force = true)
    }

    fun loadReleaseNotes(force: Boolean = false) {
        if (!force && _releaseNotes.value.isNotEmpty()) return
        releaseNotesJob?.cancel()
        releaseNotesJob = viewModelScope.launch {
            _releaseNotesLoading.value = true
            try {
                _releaseNotes.value = repository.listReleaseNotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load release notes", e)
            } finally {
                _releaseNotesLoading.value = false
            }
        }
    }

    private fun checkForUpdate(showDialogIfAvailable: Boolean, forceNetwork: Boolean, quiet: Boolean) {
        if (!forceNetwork && !repository.shouldAutoCheck()) return
        // Never interrupt an update that is on its way in.
        if (state.value.inProgress || state.value is AppUpdateUiState.ReadyToInstall) return
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            if (!quiet) check.value = AppUpdateUiState.Checking
            try {
                val info = repository.checkForUpdate()
                if (info == null) {
                    check.value = AppUpdateUiState.UpToDate
                    return@launch
                }
                check.value = AppUpdateUiState.Available(info)
                // Prompt when a newer build is available and not soft-snoozed.
                // Soft-snooze expires after [AppUpdateRepository.SNOOZE_DURATION_MS].
                if (showDialogIfAvailable && !repository.isDismissed(info.versionCode) && !_showDialog.value) {
                    showSheetFor(info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                // Only surface errors for explicit user-initiated checks.
                if (!quiet) {
                    check.value = AppUpdateUiState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Could not check for updates",
                    )
                } else if (check.value is AppUpdateUiState.Checking) {
                    check.value = AppUpdateUiState.Idle
                }
            }
        }
    }

    private fun showSheetFor(info: AppUpdateInfo) {
        _showDialog.value = true
        // Seen in the app, so the background check need not announce it as well.
        repository.markAnnounced(info.versionCode)
        AppUpdateNotifier.cancelAvailable(context)
    }

    /**
     * Hides the sheet. A download or install carries on; "Later" on an offer snoozes it
     * for [AppUpdateRepository.SNOOZE_DURATION_MS].
     */
    fun dismissDialog(snooze: Boolean = true) {
        val current = state.value
        if (snooze && !current.inProgress) current.info?.let { repository.dismiss(it.versionCode) }
        if (current is AppUpdateUiState.Error && current.info != null) installer.reset()
        pendingAfterPermission = null
        _showDialog.value = false
    }

    fun openDialog() {
        if (state.value.updateAvailable) _showDialog.value = true
    }

    /**
     * From the update notification: show the sheet, and with [start] begin at once. The
     * process may be fresh, so the build is looked up first when there is none yet.
     */
    fun openFromNotification(start: Boolean) {
        AppUpdateNotifier.cancelAvailable(context)
        viewModelScope.launch {
            if (!state.value.updateAvailable) {
                try {
                    repository.checkForUpdate()?.let { check.value = AppUpdateUiState.Available(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    check.value = AppUpdateUiState.Error(e.message ?: "Could not check for updates")
                }
            }
            val info = state.value.info ?: return@launch
            repository.markAnnounced(info.versionCode)
            _showDialog.value = true
            if (start && state.value is AppUpdateUiState.Available) update(info)
        }
    }

    /**
     * Starts the update. Returns Android's "install unknown apps" screen when DailyDash is
     * not allowed yet; the update then starts by itself on return, once it is.
     */
    fun update(info: AppUpdateInfo): Intent? {
        canInstall.value = repository.canInstallPackages()
        if (!canInstall.value) {
            pendingAfterPermission = info
            return repository.installPermissionSettingsIntent()
        }
        pendingAfterPermission = null
        installer.start(info)
        return null
    }

    fun cancelDownload() = installer.cancelDownload()

    fun retryInstall() = installer.retryInstall()

    /** Brings Android's install prompt back if it was left behind. */
    fun reopenInstallPrompt(): Boolean = installer.reopenConfirmation()

    fun setNotifyEnabled(enabled: Boolean) {
        repository.setNotifyEnabled(enabled)
        _notifyEnabled.value = enabled
    }

    override fun onCleared() {
        listenJob?.cancel()
        super.onCleared()
    }
}
