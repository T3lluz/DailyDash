package com.macrotracker.data.update

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One update from tap to reinstall, followed step by step so the sheet never shows a
 * button that no longer does anything.
 *
 * It lives for the process rather than the screen: the download carries on when the sheet
 * is closed or the activity is recreated, and Android's answer about the install comes back
 * through [UpdateInstallEvents] from the system's own callback. A closed prompt or a failed
 * install keeps the downloaded APK, so trying again installs rather than downloading again.
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: AppUpdateRepository,
) {
    sealed interface Phase {
        data object Idle : Phase

        /** [total] is null when the server did not say how big the APK is. */
        data class Downloading(val info: AppUpdateInfo, val downloaded: Long, val total: Long?) : Phase {
            val progress: Float? get() = total?.takeIf { it > 0 }?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) }
        }

        /** Handed to Android. [awaitingConfirmation] once it has put its own prompt up. */
        data class Installing(
            val info: AppUpdateInfo,
            val apkPath: String,
            val awaitingConfirmation: Boolean = false,
        ) : Phase

        /** Downloaded and not installed; [note] says why, when there is a reason. */
        data class Ready(val info: AppUpdateInfo, val apkPath: String, val note: String? = null) : Phase

        data class Failed(val info: AppUpdateInfo, val message: String) : Phase
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private var work: Job? = null
    private var watchdog: Job? = null

    /** Android's confirmation screen, kept so it can be brought back after going Home. */
    @Volatile private var confirmIntent: Intent? = null

    val busy: Boolean
        get() = _phase.value is Phase.Downloading || _phase.value is Phase.Installing

    init {
        scope.launch { UpdateInstallEvents.events.collect(::onInstallEvent) }
    }

    /** Downloads [info] (or reuses a finished download of it) and hands it to Android. */
    fun start(info: AppUpdateInfo) {
        if (busy) return
        work?.cancel()
        work = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { repository.downloadedApk(info) }
                    ?: download(info)
                install(info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Update download failed", e)
                _phase.value = Phase.Failed(info, e.message?.takeIf { it.isNotBlank() } ?: "The download failed")
            }
        }
    }

    private suspend fun download(info: AppUpdateInfo): File {
        _phase.value = Phase.Downloading(info, 0L, info.apkBytes)
        var lastShown = 0L
        return repository.downloadApk(info) { downloaded, total ->
            // A state per 64 KB read would redraw the sheet hundreds of times a second.
            val now = System.nanoTime()
            if (now - lastShown > PROGRESS_EVERY_NS || downloaded == total) {
                lastShown = now
                _phase.value = Phase.Downloading(info, downloaded, total)
            }
        }
    }

    /** Stops a download in progress; nothing half-written is kept. */
    fun cancelDownload() {
        if (_phase.value !is Phase.Downloading) return
        work?.cancel()
        _phase.value = Phase.Idle
    }

    /** Hands the downloaded APK to Android again, after its prompt was closed or the install failed. */
    fun retryInstall() {
        val ready = _phase.value as? Phase.Ready ?: return
        val file = File(ready.apkPath)
        if (!file.isFile) {
            start(ready.info)
            return
        }
        scope.launch { install(ready.info, file) }
    }

    /** Reopens Android's prompt if it was left behind; false when there is none to reopen. */
    fun reopenConfirmation(): Boolean {
        val intent = confirmIntent ?: return false
        return runCatching {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    /** Back to nothing, once the update has landed or the person has moved on from a failure. */
    fun reset() {
        if (busy) return
        _phase.value = Phase.Idle
    }

    private suspend fun install(info: AppUpdateInfo, file: File) {
        confirmIntent = null
        _phase.value = Phase.Installing(info, file.absolutePath)
        try {
            repository.cacheWhatsNew(info)
            withContext(Dispatchers.IO) { repository.installApk(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Install launch failed", e)
            _phase.value = Phase.Ready(info, file.absolutePath, "Android would not take the update: ${e.message ?: "unknown error"}")
            return
        }
        armWatchdog(info, file)
    }

    /**
     * A silent install ends with this process being replaced, and a prompted one reports
     * back. If neither happens the session was lost somewhere in the OEM's installer, and
     * the sheet should offer the button again rather than spin for ever.
     */
    private fun armWatchdog(info: AppUpdateInfo, file: File) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(INSTALL_SILENCE_MS)
            val now = _phase.value
            if (now is Phase.Installing && !now.awaitingConfirmation) {
                _phase.value = Phase.Ready(info, file.absolutePath, "Android did not answer. Try installing again.")
            }
        }
    }

    private fun onInstallEvent(event: UpdateInstallEvents.Event) {
        val current = _phase.value as? Phase.Installing ?: return
        when (event) {
            is UpdateInstallEvents.Event.AwaitingUser -> {
                watchdog?.cancel()
                confirmIntent = event.confirm
                _phase.value = current.copy(awaitingConfirmation = true)
            }
            UpdateInstallEvents.Event.Success -> watchdog?.cancel()
            UpdateInstallEvents.Event.Aborted -> {
                watchdog?.cancel()
                confirmIntent = null
                _phase.value = Phase.Ready(current.info, current.apkPath, "You closed Android's prompt. It is downloaded, so installing takes a moment.")
            }
            is UpdateInstallEvents.Event.Failed -> {
                watchdog?.cancel()
                confirmIntent = null
                _phase.value = Phase.Ready(current.info, current.apkPath, event.message)
            }
        }
    }

    private companion object {
        const val TAG = "AppUpdateInstaller"
        const val PROGRESS_EVERY_NS = 120_000_000L
        const val INSTALL_SILENCE_MS = 90_000L
    }
}
