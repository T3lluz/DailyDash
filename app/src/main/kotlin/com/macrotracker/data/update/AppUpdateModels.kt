package com.macrotracker.data.update

/**
 * A newer build discovered on GitHub Releases.
 *
 * APK assets are expected to be named:
 * `DailyDash-{versionName}-vc{versionCode}.apk`
 * e.g. `DailyDash-1.1.3-vc3.apk`
 */
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkBytes: Long?,
    val htmlUrl: String,
    val tagName: String,
)

/**
 * Historical release entry for the Settings changelog dropdown.
 * Includes past versions (not only newer ones).
 */
data class AppReleaseNotes(
    val versionName: String,
    val versionCode: Int,
    val tagName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val isNewerThanInstalled: Boolean,
)

/** Shown once after the app relaunches onto a newly installed build. */
data class WhatsNewInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
)

sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data object Checking : AppUpdateUiState()
    data object UpToDate : AppUpdateUiState()
    data class Available(val info: AppUpdateInfo) : AppUpdateUiState()

    /** A newer build, but Android has not let DailyDash install apps yet. */
    data class NeedsPermission(val info: AppUpdateInfo) : AppUpdateUiState()

    /** [progress] is null when the size is unknown; the bar then runs indeterminate. */
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : AppUpdateUiState()

    /** Handed to Android: it installs and DailyDash restarts, or it puts its own prompt up first. */
    data class Installing(val info: AppUpdateInfo, val awaitingConfirmation: Boolean) : AppUpdateUiState()

    /** Downloaded, not installed yet: Android's prompt was closed, or the install failed ([note]). */
    data class ReadyToInstall(val info: AppUpdateInfo, val apkPath: String, val note: String? = null) : AppUpdateUiState()

    /** [info] is set when the failure was the update itself, so it can be tried again. */
    data class Error(val message: String, val info: AppUpdateInfo? = null) : AppUpdateUiState()
}

/** The build this state is about, if it is about one. */
val AppUpdateUiState.info: AppUpdateInfo?
    get() = when (this) {
        is AppUpdateUiState.Available -> info
        is AppUpdateUiState.NeedsPermission -> info
        is AppUpdateUiState.Downloading -> info
        is AppUpdateUiState.Installing -> info
        is AppUpdateUiState.ReadyToInstall -> info
        is AppUpdateUiState.Error -> info
        else -> null
    }

val AppUpdateUiState.updateAvailable: Boolean
    get() = info != null

/** A download or install is under way; the sheet shows progress, not choices. */
val AppUpdateUiState.inProgress: Boolean
    get() = this is AppUpdateUiState.Downloading || this is AppUpdateUiState.Installing
