package com.macrotracker.data.server

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Keeps the live server notification on screen.
 *
 * Runs as a `specialUse` foreground service rather than `dataSync`: Android 15
 * caps dataSync at six hours per day, which would silently kill an always-on
 * monitor part-way through the afternoon. Continuous monitoring of a
 * user-configured host is exactly the case specialUse exists for.
 *
 * While the screen is off the repository drops to the slower background
 * cadence — nobody is reading the notification, and a 5-second SSH round trip
 * all night is a meaningful battery cost.
 */
@AndroidEntryPoint
class ServerMonitorService : Service() {

    @Inject lateinit var repository: ServerMonitorRepository

    @Inject lateinit var notifier: ServerNotifier

    @Inject lateinit var store: ServerStore

    @Inject lateinit var dashboard: DashboardLinkRepository

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var dashboardJob: Job? = null
    private var started = false
    private val live by lazy { ServerLiveNotification(this, notifier) }

    /**
     * Whether anyone can see the notification. With the screen off the repository drops
     * to its slower cadence and the notification is not redrawn at all — the bitmaps are
     * the expensive part, and a lock screen that is off shows nothing.
     */
    private val screenOn = MutableStateFlow(true)

    /** Mirrors screen state into the repository's polling cadence. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    repository.setBackgroundMode(false)
                    screenOn.value = true
                }
                Intent.ACTION_SCREEN_OFF -> {
                    repository.setBackgroundMode(true)
                    screenOn.value = false
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        screenOn.value = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                store.updateSettings { it.copy(liveNotificationEnabled = false) }
                stopSelf()
                return START_NOT_STICKY
            }
            ServerLiveNotification.ACTION_NEXT_SERVER -> stepServer()
            ServerLiveNotification.ACTION_TOGGLE_DETAIL ->
                store.updateSettings { it.copy(liveNotificationDetailed = !it.liveNotificationDetailed) }
        }

        // Must post a notification within a few seconds of startForegroundService,
        // so go up with a placeholder before the first poll has any data.
        if (!started) {
            startForegroundCompat(live.build(null, null, emptyList(), ServerMonitorService::class.java))
            started = true
            repository.acquire(TAG)
            observeRuntimes()
            followDashboard()
        }
        return START_STICKY
    }

    /** The Next server action: show the following enabled server, wrapping round. */
    private fun stepServer() {
        val enabled = store.profiles.value.filter { it.enabled }
        if (enabled.size < 2) return
        val current = store.settings.value.liveNotificationServerId
        val index = enabled.indexOfFirst { it.id == current }
        val next = enabled[(index + 1).mod(enabled.size)]
        store.updateSettings { it.copy(liveNotificationServerId = next.id) }
    }

    /**
     * Redraws when something the notification shows has changed: the selected server's
     * runtime, the others' state, the dashboard link, or which server is selected.
     */
    private fun observeRuntimes() {
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(
                repository.runtimes,
                store.settings.map { it.liveNotificationServerId }.distinctUntilChanged(),
                dashboard.link,
                screenOn,
                store.settings.map { it.liveNotificationDetailed }.distinctUntilChanged(),
            ) { runtimes, selectedId, link, visible, detailed ->
                val selected = selectedId?.let { runtimes[it] }?.takeIf { it.profile.enabled }
                    ?: runtimes.values.firstOrNull { it.profile.enabled }
                    ?: runtimes.values.firstOrNull()
                val others = runtimes.values.filter { it.profile.enabled && it.profile.id != selected?.profile?.id }
                    .sortedBy { it.profile.position }
                LiveFrame(
                    selected,
                    others,
                    link?.takeIf { selected != null && it.belongsTo(selected.hostProfile?.hostname) },
                    visible,
                    detailed,
                )
            }
                .distinctUntilChanged()
                .collectLatest { frame ->
                    val selected = frame.selected
                    if (selected == null) {
                        // Every server was deleted — nothing left to display.
                        stopSelf()
                        return@collectLatest
                    }
                    if (!frame.visible) return@collectLatest
                    val notification = withContext(Dispatchers.Default) {
                        live.build(selected, frame.link, frame.others, ServerMonitorService::class.java, frame.detailed)
                    }
                    runCatching {
                        NotificationManagerCompat.from(this@ServerMonitorService)
                            .notify(ServerNotifier.LIVE_NOTIFICATION_ID, notification)
                    }
                }
        }
    }

    /** The dashboard's view of its own machine, for a server that is that machine; only while the screen is on. */
    private fun followDashboard() {
        dashboardJob?.cancel()
        dashboardJob = scope.launch {
            screenOn.collectLatest { on ->
                while (on) {
                    runCatching { dashboard.refresh() }
                    delay(DASHBOARD_REFRESH_MS)
                }
            }
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ServerNotifier.LIVE_NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        dashboardJob?.cancel()
        scope.cancel()
        repository.release(TAG)
        repository.setBackgroundMode(false)
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching {
            NotificationManagerCompat.from(this).cancel(ServerNotifier.LIVE_NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    /** Everything one redraw depends on; equal frames are not drawn twice. */
    private data class LiveFrame(
        val selected: ServerRuntime?,
        val others: List<ServerRuntime>,
        val link: DashboardLink?,
        val visible: Boolean,
        val detailed: Boolean,
    )

    companion object {
        private const val TAG = "live-notification"
        const val ACTION_STOP = ServerLiveNotification.ACTION_STOP
        private const val DASHBOARD_REFRESH_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, ServerMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerMonitorService::class.java))
        }
    }
}
