package com.macrotracker.data.phone

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.graphics.scale
import com.macrotracker.BuildConfig
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.health.HealthConnectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One reading of the phone, in the shape the dashboard's phone view draws: the device,
 * battery, network, storage and memory, screen and sound, volumes, where the audio goes,
 * what is playing (and what else could), the next alarm, and (when shared) today's health
 * with the week behind it, the next events, screen time, and the weather and
 * place the app last fetched. Nothing here asks for a permission; a reading the phone may
 * not take is left out.
 *
 * Readings come in two speeds. Everything cheap is read on every report. What costs Health
 * Connect or UsageStats several calls (the week, last night's stages, workouts, vitals,
 * screen time) is kept in [PhoneExtras] and refreshed behind the report, which then goes
 * out again when it lands, so a report never waits on it.
 */
@Singleton
class PhoneSnapshot @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val health: HealthConnectRepository,
    private val calendar: CalendarRepository,
) {
    /**
     * Album art is the heavy part of a report. It is encoded again only when the picture
     * changes (keyed on the picture itself: players often send the title first and the art a
     * moment later), and sent to the bridge once per picture; after that the report carries
     * only its [artId] and the bridge reuses what it stored.
     */
    private var artKey: String? = null
    private var artData: String? = null
    private var artId: String? = null
    @Volatile private var sentArtId: String? = null

    /** The bridge has the picture now. */
    fun artSent(id: String) { sentArtId = id }

    /** The bridge lost it (a reset, a new pairing): send it again. */
    fun forgetSentArt() { sentArtId = null }

    private val extras = PhoneExtras(context, health)
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var extrasJob: Job? = null

    /** Called when the slow readings land, so the hub can send them. */
    var onExtras: (() -> Unit)? = null

    suspend fun collect(config: PhoneHubConfig, torchOn: Boolean?, light: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val out = JSONObject()
                .put("device", device())
                .put("battery", battery())
                .put("net", network())
                .put("storage", storage())
                .put("ram", memory())
                .put("screen", screen())
                .put("sys", system())
                .put("uptime", SystemClock.elapsedRealtime())
            sound(out)
            media(out)
            out.put("alarm", nextAlarm() ?: JSONObject.NULL)
            out.put("ringing", PhoneRinger.ringing)
            if (torchOn != null) out.put("torch", JSONObject().put("on", torchOn))
            out.put(
                "caps",
                JSONObject()
                    .put("notifications", PhoneNotificationListener.connected && config.notifications)
                    .put("media", PhoneNotificationListener.connected)
                    .put("health", config.health)
                    .put("calendar", config.calendar)
                    .put("location", config.location)
                    .put("usage", PhoneExtras.usageGranted(context))
                    .put("commands", config.commands)
                    // What this build can do, so the dashboard offers only that. `hub` counts protocol changes.
                    .put("hub", HUB_VERSION)
                    .put("cmds", JSONArray(COMMANDS)),
            )
            // A report right after a command only needs what the command could change.
            if (light) return@withContext out
            if (config.health) {
                val today = healthToday()
                val more = extras.health
                if (today != null || more != null) {
                    val h = today ?: JSONObject()
                    more?.keys()?.forEach { k -> h.put(k, more.get(k)) }
                    h.put("stepsGoal", STEP_GOAL)
                    out.put("health", h)
                }
            }
            extras.usage?.let { out.put("usage", it) }
            if (config.calendar) out.put("calendar", nextEvents())
            if (config.location) weatherAndPlace(out)
            refreshExtras(config)
            out
        }

    /** Just what playing music changes: the fast report sent on every track or state change. */
    suspend fun collectMedia(): JSONObject = withContext(Dispatchers.IO) {
        val out = JSONObject()
        sound(out)
        media(out)
        out.put("ringing", PhoneRinger.ringing)
        out
    }

    private fun refreshExtras(config: PhoneHubConfig) {
        if (extrasJob?.isActive == true || !extras.stale(config)) return
        extrasJob = bg.launch {
            if (extras.refresh(config)) onExtras?.invoke()
        }
    }

    private fun device(): JSONObject {
        val name = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        return JSONObject()
            .put("name", name?.takeIf { it.isNotBlank() } ?: Build.MODEL)
            .put("model", Build.MODEL)
            .put("maker", Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("patch", Build.VERSION.SECURITY_PATCH)
            .put("app", BuildConfig.VERSION_NAME)
    }

    private fun battery(): JSONObject {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return JSONObject()
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val bm = context.getSystemService(BatteryManager::class.java)
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0
        val o = JSONObject()
            .put("pct", if (level >= 0) 100.0 * level / scale else JSONObject.NULL)
            .put("charging", charging)
            .put("full", status == BatteryManager.BATTERY_STATUS_FULL)
            .put(
                "plug",
                when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "charger"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                    BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
                    else -> ""
                },
            )
            .put("temp", i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
            .put("volt", volt)
            .put(
                "health",
                when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                    else -> "Unknown"
                },
            )
            .put("powerSave", context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
        // Current: most phones report µA, a few mA. Anything under 20 A in µA is taken as µA.
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { raw ->
            val ma = if (kotlin.math.abs(raw) > 20_000) raw / 1000.0 else raw.toDouble()
            o.put("mA", Math.round(ma))
            if (volt > 0) o.put("watts", Math.round(kotlin.math.abs(ma) * volt / 100.0) / 10.0)
        }
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 && level > 0 }?.let { uah ->
            o.put("mAh", uah / 1000)
            o.put("capMah", Math.round(uah / 1000.0 / (level.toDouble() / scale)))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }?.let { o.put("cycles", it) }
        }
        if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bm?.computeChargeTimeRemaining()?.takeIf { it > 0 }?.let { o.put("fullInMs", it) }
        }
        return o
    }

    private fun network(): JSONObject {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return JSONObject()
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return JSONObject().put("type", "none")
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val o = JSONObject()
            .put("type", type)
            // Tailscale is a VPN; the dashboard is only reachable through it.
            .put("vpn", cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true })
            .put("metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            .put("validated", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .put("down", caps.linkDownstreamBandwidthKbps)
            .put("up", caps.linkUpstreamBandwidthKbps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }?.let { o.put("dbm", it) }
        }
        if (type == "wifi") {
            // The name needs location access, which the weather already asked for; without it Android says <unknown ssid>.
            @Suppress("DEPRECATION")
            runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()?.let { w ->
                w.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }?.let { o.put("ssid", it) }
                if (w.linkSpeed > 0) o.put("mbps", w.linkSpeed)
                if (w.frequency > 0) o.put("ghz", if (w.frequency > 5900) 6 else if (w.frequency > 4900) 5 else 2.4)
                if (!o.has("dbm") && w.rssi > -127) o.put("dbm", w.rssi)
            }
        }
        val tm = context.getSystemService(TelephonyManager::class.java)
        tm?.networkOperatorName?.takeIf { it.isNotBlank() }?.let { o.put("carrier", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { tm?.signalStrength?.level }.getOrNull()?.let { o.put("bars", it) }
        }
        return o
    }

    private fun storage(): JSONObject {
        val fs = StatFs(Environment.getDataDirectory().path)
        return JSONObject().put("total", fs.totalBytes).put("free", fs.availableBytes)
    }

    private fun memory(): JSONObject {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return JSONObject().put("total", info.totalMem).put("avail", info.availMem).put("low", info.lowMemory)
    }

    private fun screen(): JSONObject = JSONObject()
        .put("on", context.getSystemService(PowerManager::class.java)?.isInteractive == true)
        .put("locked", context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true)

    /** Switches and states people glance at in quick settings. */
    private fun system(): JSONObject {
        val cr = context.contentResolver
        val o = JSONObject()
        runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
            ?.let { o.put("brightness", Math.round(it * 100 / 255.0)) }
        runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) }.getOrNull()
            ?.let { o.put("autoBright", it == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) }
        runCatching { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION) }.getOrNull()
            ?.let { o.put("rotate", it == 1) }
        runCatching { Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON) }.getOrNull()
            ?.let { o.put("airplane", it == 1) }
        runCatching { Settings.Global.getInt(cr, Settings.Global.BLUETOOTH_ON) }.getOrNull()
            ?.let { o.put("bluetooth", it != 0) }
        runCatching { context.getSystemService(LocationManager::class.java)?.isLocationEnabled }.getOrNull()
            ?.let { o.put("location", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus?.let { s ->
                o.put(
                    "thermal",
                    when (s) {
                        PowerManager.THERMAL_STATUS_NONE -> "normal"
                        PowerManager.THERMAL_STATUS_LIGHT -> "warm"
                        PowerManager.THERMAL_STATUS_MODERATE -> "hot"
                        PowerManager.THERMAL_STATUS_SEVERE -> "throttling"
                        else -> "critical"
                    },
                )
            }
        }
        return o
    }

    private fun sound(out: JSONObject) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        out.put(
            "ringer",
            when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            },
        )
        out.put(
            "dnd",
            when (context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority only"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms only"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "total silence"
                else -> "off"
            },
        )
        fun stream(s: Int) = JSONArray().put(am.getStreamVolume(s)).put(am.getStreamMaxVolume(s))
        out.put(
            "volume",
            JSONObject()
                .put("media", stream(AudioManager.STREAM_MUSIC))
                .put("ring", stream(AudioManager.STREAM_RING))
                .put("notif", stream(AudioManager.STREAM_NOTIFICATION))
                .put("alarm", stream(AudioManager.STREAM_ALARM))
                .put("call", stream(AudioManager.STREAM_VOICE_CALL)),
        )
        // Where sound goes: headphones win, as they do for Android's own routing.
        val outs = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        val rank = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI,
        )
        val best = outs.filter { it.type in rank }.minByOrNull { rank.indexOf(it.type) }
        val kind = when (best?.type) {
            null -> "speaker"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID -> "bluetooth"
            AudioDeviceInfo.TYPE_HDMI -> "hdmi"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
            else -> "wired"
        }
        out.put(
            "audio",
            JSONObject()
                .put("kind", kind)
                .put("name", best?.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: if (best == null) "Phone speaker" else "Headphones")
                .put("playing", am.isMusicActive)
                .put("call", am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION)
                .put("micMuted", am.isMicrophoneMute),
        )
    }

    private fun media(out: JSONObject) {
        out.put("media", PhoneMedia.controller?.let(::mediaOf) ?: JSONObject.NULL)
        val all = PhoneMedia.sessions
        val current = PhoneMedia.controller?.sessionToken
        out.put(
            "sessions",
            JSONArray().apply {
                all.take(5).forEach { c ->
                    val md = c.metadata ?: return@forEach
                    val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return@forEach
                    put(
                        JSONObject()
                            .put("pkg", c.packageName)
                            .put("app", appLabel(c.packageName))
                            .put("title", title)
                            .put("artist", md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
                            .put("state", stateOf(c.playbackState))
                            .put("current", c.sessionToken == current),
                    )
                }
            },
        )
    }

    private fun stateOf(st: PlaybackState?) = when (st?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        else -> "stopped"
    }

    private fun appLabel(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun mediaOf(c: MediaController): JSONObject? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val st = c.playbackState
        val o = JSONObject()
            .put("app", appLabel(c.packageName))
            .put("pkg", c.packageName)
            .put("title", title)
            .put("artist", md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "")
            .put("album", md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")
            .put("dur", md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0))
            .put("state", stateOf(st))
        md.getLong(MediaMetadata.METADATA_KEY_YEAR).takeIf { it in 1900..2100 }?.let { o.put("year", it) }
        md.getString(MediaMetadata.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }?.let { o.put("genre", it) }
        md.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER).takeIf { it > 0 }?.let { o.put("track", it) }
        if (st != null) {
            // Where the track is now, extrapolated from the last position the player reported.
            val sinceUpdate = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
            val playing = st.state == PlaybackState.STATE_PLAYING
            val pos = if (playing) st.position + (sinceUpdate * st.playbackSpeed).toLong() else st.position
            o.put("pos", pos.coerceAtLeast(0)).put("at", System.currentTimeMillis())
            if (playing && st.playbackSpeed > 0f && st.playbackSpeed != 1f) o.put("speed", st.playbackSpeed.toDouble())
            val a = st.actions
            o.put(
                "can",
                JSONObject()
                    .put("seek", a and PlaybackState.ACTION_SEEK_TO != 0L)
                    .put("next", a and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
                    .put("prev", a and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L),
            )
            st.customActions?.take(4)?.takeIf { it.isNotEmpty() }?.let { list ->
                o.put(
                    "custom",
                    JSONArray().apply { list.forEach { put(JSONObject().put("id", it.action).put("name", it.name.toString())) } },
                )
            }
            // What plays next, where the player shares its queue.
            val queue = runCatching { c.queue }.getOrNull()
            if (!queue.isNullOrEmpty()) {
                val at = queue.indexOfFirst { it.queueId == st.activeQueueItemId }
                val next = queue.drop(if (at >= 0) at + 1 else 0).take(5)
                if (next.isNotEmpty()) {
                    o.put(
                        "queue",
                        JSONArray().apply {
                            next.forEach { q ->
                                put(
                                    JSONObject()
                                        .put("title", q.description.title?.toString() ?: "")
                                        .put("sub", q.description.subtitle?.toString() ?: ""),
                                )
                            }
                        },
                    )
                }
            }
        }
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val key = "${c.packageName}|$title|${o.optString("album")}|${bmp?.let(::fingerprint)}"
        if (key != artKey) {
            artKey = key
            // Big enough for the dashboard's player at twice its size.
            artData = bmp?.let { jpegDataUrl(it, 600, 86) }
            artId = artData?.let { sha1(it).take(16) }
        }
        artId?.let { id ->
            o.put("artId", id)
            if (id != sentArtId) o.put("art", artData)
        }
        return o
    }

    private fun nextAlarm(): JSONObject? {
        val info = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock ?: return null
        val pkg = runCatching { info.showIntent?.creatorPackage }.getOrNull()
        return JSONObject().put("at", info.triggerTime).put("app", pkg?.let(::appLabel) ?: "")
    }

    private suspend fun healthToday(): JSONObject? {
        val s = withTimeoutOrNull(8_000) { runCatching { health.readTodayStats() }.getOrNull() } ?: return null
        val o = JSONObject()
        if (s.steps > 0) o.put("steps", s.steps)
        if (s.avgHeartRate > 0) o.put("hr", s.avgHeartRate)
        if (s.restingHeartRate > 0) o.put("rhr", s.restingHeartRate)
        if (s.sleepMinutes > 0) o.put("sleepMin", s.sleepMinutes)
        if (s.activeCaloriesBurned > 0) o.put("kcal", s.activeCaloriesBurned.toInt())
        if (s.totalCaloriesBurned > 0) o.put("kcalTotal", s.totalCaloriesBurned.toInt())
        if (s.distance > 0) o.put("distanceM", s.distance.toInt())
        if (s.floorsClimbed > 0) o.put("floors", s.floorsClimbed)
        if (s.oxygenSaturation > 0) o.put("spo2", s.oxygenSaturation)
        if (s.respiratoryRate > 0) o.put("resp", Math.round(s.respiratoryRate * 10) / 10.0)
        return o.takeIf { it.length() > 0 }
    }

    private suspend fun nextEvents(): JSONArray {
        val out = JSONArray()
        if (!calendar.hasPermission()) return out
        val zone = ZoneId.systemDefault()
        val now = java.time.LocalDateTime.now()
        // Same window as the Home card, so both share the repository's cache.
        runCatching { calendar.readEvents(extraDays = CalendarRepository.WINDOW_DAYS) }.getOrDefault(emptyList())
            .filter { it.endTime.isAfter(now) }
            .distinctBy { it.id to it.beginMillis }
            .sortedWith(compareBy({ maxOf(it.startTime.toLocalDate(), now.toLocalDate()) }, { it.isAllDay }, { it.startTime }))
            .take(14)
            .forEach { e ->
                out.put(
                    JSONObject()
                        .put("title", e.title)
                        .put("start", e.startTime.atZone(zone).toInstant().toEpochMilli())
                        .put("end", e.endTime.atZone(zone).toInstant().toEpochMilli())
                        .put("allDay", e.isAllDay)
                        .put("location", e.location)
                        .put("cal", e.calendarName)
                        .put("color", "#%06X".format(e.calendarColor and 0xFFFFFF)),
                )
            }
        return out
    }

    /** The forecast and place the app itself last fetched; the hub never asks for a location fix. */
    private fun weatherAndPlace(out: JSONObject) {
        val p = context.getSharedPreferences("daily_dash_weather_cache", Context.MODE_PRIVATE)
        val at = p.getLong("fetched_at", 0L)
        if (at == 0L) return
        val temp = p.getString("temp", null)?.toDoubleOrNull() ?: return
        val place = p.getString("location", null).orEmpty()
        out.put(
            "weather",
            JSONObject()
                .put("temp", temp)
                .put("desc", p.getString("description", "") ?: "")
                .put("loc", place)
                .put("hi", p.getString("high", null)?.toDoubleOrNull() ?: JSONObject.NULL)
                .put("lo", p.getString("low", null)?.toDoubleOrNull() ?: JSONObject.NULL)
                .put("symbol", p.getString("symbol_code", "") ?: "")
                .put("at", at),
        )
        val lat = p.getString("latitude", null)?.toDoubleOrNull()
        val lon = p.getString("longitude", null)?.toDoubleOrNull()
        if (lat != null && lon != null) {
            out.put("location", JSONObject().put("lat", lat).put("lon", lon).put("at", at).put("place", place))
        }
    }

    /** Cheap identity for a bitmap: its size and a grid of sampled pixels. */
    private fun fingerprint(b: Bitmap): Int = runCatching {
        var h = b.width * 31 + b.height
        val w = b.width.coerceAtLeast(1)
        val hh = b.height.coerceAtLeast(1)
        for (y in 1..4) for (x in 1..4) h = h * 31 + b.getPixel((w * x / 5).coerceIn(0, w - 1), (hh * y / 5).coerceIn(0, hh - 1))
        h
    }.getOrDefault(b.width * 31 + b.height)

    private fun sha1(s: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** The step goal the Health tab measures against. */
        const val STEP_GOAL = 10_000L

        /** Bumped when the dashboard can rely on something new; 2 = art ids, media lane, inline commands. */
        const val HUB_VERSION = 2

        /** Commands this build runs; the dashboard hides the rest. */
        val COMMANDS = listOf(
            "ring", "stop-ring", "torch", "open-url", "clipboard", "note", "media", "volume", "ringer", "dnd",
            "action", "dismiss", "dismiss-all", "reply", "refresh",
        )

        /** A small JPEG as a data URL, for album art and app icons in a report. */
        fun jpegDataUrl(src: Bitmap, max: Int, quality: Int = 78): String {
            val scale = max.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1)
            val bmp = if (scale < 1f) src.scale((src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1)) else src
            val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        fun pngDataUrl(bmp: Bitmap): String {
            val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
