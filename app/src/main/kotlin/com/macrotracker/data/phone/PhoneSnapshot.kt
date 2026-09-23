package com.macrotracker.data.phone

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One reading of the phone, in the shape the dashboard's phone view draws: the device,
 * battery, network, storage and memory, screen and sound, volumes, what is playing, and
 * (when shared) today's health, the next events, and the weather and place the app last
 * fetched. Nothing here asks for a permission; a reading the phone may not take is left out.
 */
@Singleton
class PhoneSnapshot @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val health: HealthConnectRepository,
    private val calendar: CalendarRepository,
) {
    /** Album art is the heavy part of a report; it is encoded again only when the track changes. */
    private var artKey: String? = null
    private var artData: String? = null

    suspend fun collect(config: PhoneHubConfig, torchOn: Boolean?): JSONObject = withContext(Dispatchers.IO) {
        val out = JSONObject()
            .put("device", device())
            .put("battery", battery())
            .put("net", network())
            .put("storage", storage())
            .put("ram", memory())
            .put("screen", screen())
            .put("uptime", SystemClock.elapsedRealtime())
        sound(out)
        out.put("media", PhoneMedia.controller?.let(::media) ?: JSONObject.NULL)
        if (torchOn != null) out.put("torch", JSONObject().put("on", torchOn))
        out.put(
            "caps",
            JSONObject()
                .put("notifications", PhoneNotificationListener.connected && config.notifications)
                .put("media", PhoneNotificationListener.connected)
                .put("health", config.health)
                .put("calendar", config.calendar)
                .put("location", config.location)
                .put("commands", config.commands),
        )
        if (config.health) healthToday()?.let { out.put("health", it) }
        if (config.calendar) out.put("calendar", nextEvents())
        if (config.location) weatherAndPlace(out)
        out
    }

    private fun device(): JSONObject {
        val name = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        return JSONObject()
            .put("name", name?.takeIf { it.isNotBlank() } ?: Build.MODEL)
            .put("model", Build.MODEL)
            .put("maker", Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("app", BuildConfig.VERSION_NAME)
    }

    private fun battery(): JSONObject {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return JSONObject()
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val bm = context.getSystemService(BatteryManager::class.java)
        val o = JSONObject()
            .put("pct", if (level >= 0) 100.0 * level / scale else JSONObject.NULL)
            .put("charging", charging)
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
            .put("volt", i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0)
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
            .put("down", caps.linkDownstreamBandwidthKbps)
            .put("up", caps.linkUpstreamBandwidthKbps)
        if (type == "cellular") {
            context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
                ?.takeIf { it.isNotBlank() }?.let { o.put("carrier", it) }
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
        return JSONObject().put("total", info.totalMem).put("avail", info.availMem)
    }

    private fun screen(): JSONObject = JSONObject()
        .put("on", context.getSystemService(PowerManager::class.java)?.isInteractive == true)
        .put("locked", context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true)

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
                .put("alarm", stream(AudioManager.STREAM_ALARM)),
        )
    }

    private fun media(c: MediaController): JSONObject? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val st = c.playbackState
        val app = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        val o = JSONObject()
            .put("app", app)
            .put("pkg", c.packageName)
            .put("title", title)
            .put("artist", md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "")
            .put("album", md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")
            .put("dur", md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0))
            .put(
                "state",
                when (st?.state) {
                    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> "playing"
                    PlaybackState.STATE_PAUSED -> "paused"
                    else -> "stopped"
                },
            )
        if (st != null) {
            // Where the track is now, extrapolated from the last position the player reported.
            val sinceUpdate = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
            val pos = if (st.state == PlaybackState.STATE_PLAYING) st.position + (sinceUpdate * st.playbackSpeed).toLong() else st.position
            o.put("pos", pos.coerceAtLeast(0)).put("at", System.currentTimeMillis())
        }
        val key = "${c.packageName}|$title|${o.optString("album")}"
        if (key != artKey) {
            artKey = key
            val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            artData = bmp?.let { jpegDataUrl(it, 200) }
        }
        artData?.let { o.put("art", it) }
        return o
    }

    private suspend fun healthToday(): JSONObject? {
        val s = withTimeoutOrNull(8_000) { runCatching { health.readTodayStats() }.getOrNull() } ?: return null
        val o = JSONObject()
        if (s.steps > 0) o.put("steps", s.steps)
        if (s.avgHeartRate > 0) o.put("hr", s.avgHeartRate)
        if (s.restingHeartRate > 0) o.put("rhr", s.restingHeartRate)
        if (s.sleepMinutes > 0) o.put("sleepMin", s.sleepMinutes)
        if (s.activeCaloriesBurned > 0) o.put("kcal", s.activeCaloriesBurned.toInt())
        if (s.distance > 0) o.put("distanceM", s.distance.toInt())
        if (s.floorsClimbed > 0) o.put("floors", s.floorsClimbed)
        if (s.oxygenSaturation > 0) o.put("spo2", s.oxygenSaturation)
        return o.takeIf { it.length() > 0 }
    }

    private suspend fun nextEvents(): JSONArray {
        val out = JSONArray()
        if (!calendar.hasPermission()) return out
        val zone = ZoneId.systemDefault()
        val now = java.time.LocalDateTime.now()
        // Same window as the Home card, so both share the repository's cache.
        runCatching { calendar.readEvents(extraDays = 14) }.getOrDefault(emptyList())
            .filter { it.endTime.isAfter(now) }
            .distinctBy { it.id to it.beginMillis }
            .sortedWith(compareBy({ maxOf(it.startTime.toLocalDate(), now.toLocalDate()) }, { it.isAllDay }, { it.startTime }))
            .take(5)
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
                .put("symbol", p.getString("symbol_code", "") ?: ""),
        )
        val lat = p.getString("latitude", null)?.toDoubleOrNull()
        val lon = p.getString("longitude", null)?.toDoubleOrNull()
        if (lat != null && lon != null) {
            out.put("location", JSONObject().put("lat", lat).put("lon", lon).put("at", at).put("place", place))
        }
    }

    companion object {
        /** A small JPEG as a data URL, for album art and app icons in a report. */
        fun jpegDataUrl(src: Bitmap, max: Int): String {
            val scale = max.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1)
            val bmp = if (scale < 1f) src.scale((src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1)) else src
            val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 78, it) }.toByteArray()
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        fun pngDataUrl(bmp: Bitmap): String {
            val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
