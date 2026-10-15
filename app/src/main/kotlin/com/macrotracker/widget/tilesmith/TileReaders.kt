package com.macrotracker.widget.tilesmith

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import com.macrotracker.data.google.GoogleAccess
import com.macrotracker.data.google.GoogleScope
import com.macrotracker.data.phone.PhoneMedia
import com.macrotracker.data.phone.PhoneNotificationListener
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tilesmith's readers for what the phone and your accounts know beyond the ready-made
 * widgets' stores: now playing and notifications (notification access), contacts, photos,
 * Bluetooth, YouTube and Twitch, Gmail and Google Tasks, and the dashboard's Coming up.
 * Each takes a group's due blocks and answers a value per binding id, like [TileResolver]'s own.
 */
internal object TileReaders {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** "2m", "3h", "Yesterday", "Mon", "12 Oct": how long ago, for a row's end. */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = (now - ms).coerceAtLeast(0)
        val min = d / 60_000
        return when {
            min < 1 -> "Now"
            min < 60 -> "${min}m"
            min < 24 * 60 -> "${min / 60}h"
            else -> {
                val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                val today = LocalDate.now(zone)
                when {
                    day == today.minusDays(1) -> "Yesterday"
                    day.isAfter(today.minusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                }
            }
        }
    }

    /** "Today", "Tomorrow", "Fri", "24 Dec". */
    fun dayWord(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date.isAfter(today) && date.isBefore(today.plusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }
    }

    /** "12k", "1.2M", "842". */
    fun compact(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 10_000 -> "${n / 1000}k"
        n >= 1000 -> "%.1fk".format(n / 1000.0).replace(".0k", "k")
        else -> n.toString()
    }

    /** The comma-separated parts of a block's query, lowercased. */
    private fun terms(q: String?): List<String> = q?.split(',', ';')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun hashKey(vararg parts: String?): String = Integer.toHexString(parts.joinToString("\u0000") { it.orEmpty() }.hashCode())

    // ── Now playing and notifications ───────────────────────────────────────

    private fun mediaController(context: Context): MediaController? {
        PhoneMedia.controller?.let { return it }
        if (!PhoneNotificationListener.granted(context)) return null
        val list = runCatching {
            context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(PhoneNotificationListener.component(context))
        }.getOrNull().orEmpty()
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }

    fun media(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        if (!PhoneNotificationListener.granted(context)) return items.associate { it.first to TileValue.needs("Allow notification access for what's playing") }
        val v = nowPlaying(context)
        val all = if (items.any { it.second.bind != "media.now" }) notifications(context) else emptyList()
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "media.now" -> v
                "notify.count", "notify.list" -> {
                    if (!PhoneNotificationListener.connected) {
                        TileValue.error("Notification access is reconnecting")
                    } else {
                        val t = terms(b.query)
                        val rows = all.filter { n -> t.isEmpty() || t.any { n.app.lowercase().contains(it) || n.pkg.lowercase().contains(it) } }
                        if (b.bind == "notify.count") {
                            TileValue(number = rows.size.toDouble(), sub = if (rows.size == 1) "notification" else "notifications", icon = "bell")
                        } else if (rows.isEmpty()) {
                            TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "No notifications")
                        } else {
                            TileValue(
                                number = rows.size.toDouble(),
                                items = rows.take(b.max ?: 12).map { n ->
                                    TileItem(n.title.ifBlank { n.app }, n.text.ifBlank { null }, "${n.app} · ${ago(n.at)}", n.color, link = "package:${n.pkg}")
                                },
                            )
                        }
                    }
                }
                else -> TileValue.error("Unknown source")
            }
        }
    }

    private fun nowPlaying(context: Context): TileValue {
        val c = mediaController(context) ?: return TileValue(text = "Nothing playing", sub = "Play something to see it here", icon = "music", active = false)
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val image = art?.let { runCatching { TileImages.storeLocal(context, "media_" + hashKey(c.packageName, title, artist), it, 512) }.getOrNull() }
        val pm = context.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
        return TileValue(
            text = title?.takeIf { it.isNotBlank() } ?: app,
            sub = artist?.takeIf { it.isNotBlank() } ?: app,
            unit = app,
            image = image,
            active = playing,
            icon = "music",
            url = "package:${c.packageName}",
        )
    }

    private data class Note(val pkg: String, val app: String, val title: String, val text: String, val at: Long, val color: String?)

    private fun notifications(context: Context): List<Note> {
        val l = PhoneNotificationListener.instance ?: return emptyList()
        return l.all().mapNotNull { sbn ->
            val o = PhoneNotificationListener.toJson(context, sbn) ?: return@mapNotNull null
            Note(o.optString("pkg"), o.optString("app"), o.optString("title"), o.optString("text"), o.optLong("at"), o.optString("color").ifBlank { null })
        }.sortedByDescending { it.at }
    }

    // ── Contacts ────────────────────────────────────────────────────────────

    fun people(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return items.associate { it.first to TileValue.needs("Allow contacts") }
        return items.associate { (id, b) ->
            id to runCatching {
                when (b.bind) {
                    "contacts.birthdays" -> birthdays(context, b.max ?: 6)
                    "contacts.favorites" -> favorites(context, b.max ?: 8)
                    else -> TileValue.error("Unknown source")
                }
            }.getOrElse { TileValue.error("Couldn't read contacts") }
        }
    }

    private fun contactPhoto(context: Context, contactId: Long, thumb: String?): String? {
        val uri = thumb?.let(Uri::parse) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { s -> TileImages.storeLocalBytes(context, "contact_${contactId}_${hashKey(thumb)}", s.readBytes(), 256) }
        }.getOrNull()
    }

    private fun birthdays(context: Context, max: Int): TileValue {
        val cr = context.contentResolver
        val today = LocalDate.now(zone)
        data class B(val name: String, val next: LocalDate, val turns: Int?, val image: String?, val link: String)
        val out = ArrayList<B>()
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY, ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Event.START_DATE, ContactsContract.Data.PHOTO_THUMBNAIL_URI,
            ),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val raw = c.getString(3)?.trim().orEmpty()
                val (year, md) = parseBirthday(raw) ?: continue
                var next = runCatching { md.atYear(today.year) }.getOrNull() ?: continue
                if (next.isBefore(today)) next = md.atYear(today.year + 1)
                val turns = year?.let { next.year - it }?.takeIf { it in 1..120 }
                val link = ContactsContract.Contacts.getLookupUri(id, c.getString(1)).toString()
                out += B(c.getString(2).orEmpty(), next, turns, contactPhoto(context, id, c.getString(4)), link)
            }
        }
        val rows = out.distinctBy { it.name to it.next }.sortedBy { it.next }.take(max)
        if (rows.isEmpty()) return TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "No birthdays in your contacts")
        val first = rows.first()
        return TileValue(
            text = first.name,
            sub = if (first.next == today) "Birthday today" else "Birthday ${dayWord(first.next)}",
            atMs = first.next.atStartOfDay(zone).toInstant().toEpochMilli(),
            icon = "cake",
            items = rows.map { r -> TileItem(r.name, r.turns?.let { "Turns $it" }, dayWord(r.next), if (r.next == today) "#F1B467" else null, r.image, r.link) },
        )
    }

    /** `1990-05-17` or `--05-17` (no year), as contacts apps store them. */
    internal fun parseBirthday(raw: String): Pair<Int?, MonthDay>? {
        val s = raw.trim()
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let { m ->
            return runCatching { m.groupValues[1].toInt() to MonthDay.of(m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("""^--(\d{1,2})-?(\d{1,2})""").find(s)?.let { m ->
            return runCatching { null to MonthDay.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
        }
        Regex("""^(\d{1,2})[./](\d{1,2})[./](\d{4})$""").find(s)?.let { m ->
            return runCatching { m.groupValues[3].toInt() to MonthDay.of(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    private fun favorites(context: Context, max: Int): TileValue {
        val rows = ArrayList<TileItem>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.Contacts.STARRED} = 1",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE",
        )?.use { c ->
            while (c.moveToNext() && rows.size < max) {
                val id = c.getLong(0)
                rows += TileItem(
                    c.getString(2).orEmpty(),
                    image = contactPhoto(context, id, c.getString(3)),
                    link = ContactsContract.Contacts.getLookupUri(id, c.getString(1)).toString(),
                )
            }
        }
        return if (rows.isEmpty()) TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "Star someone in Contacts to see them here")
        else TileValue(items = rows, number = rows.size.toDouble(), icon = "users")
    }

    // ── Photos ──────────────────────────────────────────────────────────────

    /** The permission that lets DailyDash read photos on this Android version. */
    val photoPermissions: Set<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> setOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> setOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> setOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Whether any photos can be read: all of them, or the ones picked on Android 14's partial access. */
    fun photosGranted(context: Context): Boolean = photoPermissions.any { granted(context, it) }

    private data class Photo(val id: Long, val bucket: String, val at: Long) {
        val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    private fun queryPhotos(context: Context, query: String?, limit: Int): List<Photo> {
        val t = terms(query)
        val out = ArrayList<Photo>()
        val sel = if (t.isEmpty()) null else t.joinToString(" OR ") { "LOWER(${MediaStore.Images.Media.BUCKET_DISPLAY_NAME}) LIKE ?" }
        val args = if (t.isEmpty()) null else t.map { "%$it%" }.toTypedArray()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED),
            sel,
            args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val taken = c.getLong(2).takeIf { it > 0 } ?: (c.getLong(3) * 1000)
                out += Photo(c.getLong(0), c.getString(1).orEmpty(), taken)
            }
        }
        return out
    }

    private fun photoImage(context: Context, p: Photo, px: Int): String? {
        val key = "photo_${p.id}_$px"
        if (TileImages.has(context, "local:$key")) return "local:$key"
        val bmp = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(p.uri, Size(px, px), null)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(p.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var s = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (s * 2) >= px) s *= 2
                context.contentResolver.openInputStream(p.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            }
        }.getOrNull() ?: return null
        return TileImages.storeLocal(context, key, bmp, px).also { bmp.recycle() }
    }

    fun photos(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        if (!photosGranted(context)) return items.associate { it.first to TileValue.needs("Allow photos") }
        return items.associate { (id, b) ->
            id to runCatching {
                when (b.bind) {
                    "photos.random" -> {
                        val pool = queryPhotos(context, b.query, 400)
                        val p = pool.randomOrNull() ?: return@runCatching TileValue.empty(if (b.query.isNullOrBlank()) "No photos yet" else "No photos in ${b.query}")
                        val day = Instant.ofEpochMilli(p.at).atZone(zone).toLocalDate()
                        TileValue(
                            text = p.bucket.ifBlank { "Photo" },
                            sub = day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())),
                            image = photoImage(context, p, 900),
                            url = p.uri.toString(),
                            icon = "image",
                        )
                    }
                    "photos.recent" -> {
                        val list = queryPhotos(context, b.query, b.max ?: 10)
                        if (list.isEmpty()) TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "No photos yet")
                        else TileValue(
                            number = list.size.toDouble(),
                            items = list.map { p ->
                                TileItem(dayWord(Instant.ofEpochMilli(p.at).atZone(zone).toLocalDate()), p.bucket.ifBlank { null }, ago(p.at), image = photoImage(context, p, 720), link = p.uri.toString())
                            },
                        )
                    }
                    else -> TileValue.error("Unknown source")
                }
            }.getOrElse { TileValue.error("Couldn't read photos") }
        }
    }

    // ── Bluetooth ───────────────────────────────────────────────────────────

    val bluetoothPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH

    fun bluetoothGranted(context: Context): Boolean = granted(context, bluetoothPermission)

    private data class Bt(val name: String, val connected: Boolean, val battery: Int?)

    /** The profiles headphones, speakers, cars and hearing aids connect over; watches show up on GATT. */
    private val audioProfiles: List<Int>
        get() = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET) +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(BluetoothProfile.HEARING_AID) else emptyList()) +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(BluetoothProfile.LE_AUDIO) else emptyList())

    /** Addresses connected right now, through the public profile proxies (each answers on the main thread). */
    @SuppressLint("MissingPermission")
    private suspend fun connectedAddresses(context: Context, manager: BluetoothManager): Set<String> {
        val adapter = manager.adapter ?: return emptySet()
        val out = HashSet<String>()
        runCatching { manager.getConnectedDevices(BluetoothProfile.GATT).forEach { out += it.address } }
        for (profile in audioProfiles) {
            val devices = withTimeoutOrNull(1_500L) {
                suspendCancellableCoroutine<List<BluetoothDevice>> { cont ->
                    val asked = runCatching {
                        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                                val list = runCatching { proxy.connectedDevices.toList() }.getOrDefault(emptyList())
                                runCatching { adapter.closeProfileProxy(p, proxy) }
                                if (cont.isActive) cont.resume(list)
                            }

                            override fun onServiceDisconnected(p: Int) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        }, profile)
                    }.getOrDefault(false)
                    if (!asked && cont.isActive) cont.resume(emptyList())
                }
            }.orEmpty()
            devices.forEach { out += it.address }
        }
        return out
    }

    @SuppressLint("MissingPermission")
    private suspend fun bluetoothDevices(context: Context): List<Bt>? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        val adapter = manager.adapter ?: return null
        if (!adapter.isEnabled) return emptyList()
        val connected = connectedAddresses(context, manager)
        return adapter.bondedDevices.orEmpty().map { d ->
            // Battery isn't in the SDK; every Android since 8 has it, and without it a device just shows no battery.
            val battery = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int }.getOrNull()?.takeIf { it in 0..100 }
            Bt(d.name ?: d.address, d.address in connected, battery)
        }.sortedWith(compareByDescending<Bt> { it.connected }.thenBy { it.name.lowercase() })
    }

    suspend fun bluetooth(context: Context, b: TileBlock): TileValue {
        if (!bluetoothGranted(context)) return TileValue.needs("Allow Bluetooth")
        val list = runCatching { bluetoothDevices(context) }.getOrNull() ?: return TileValue.error("No Bluetooth on this phone")
        val on = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        if (!on) return TileValue(text = "Bluetooth off", state = TileValueState.EMPTY, note = "Bluetooth is off", icon = "bluetooth")
        return when (b.bind) {
            "phone.bluetooth" -> TileValue(
                number = list.count { it.connected }.toDouble(),
                sub = "${list.count { it.connected }} connected",
                icon = "bluetooth",
                items = list.take(b.max ?: 6).map { d -> TileItem(d.name, if (d.connected) "Connected" else null, d.battery?.let { "$it%" } ?: if (d.connected) "On" else "—", if (d.connected) "#3FA266" else null) },
                active = list.any { it.connected },
            )
            else -> {
                val t = terms(b.query)
                val pick = list.filter { it.connected }.filter { t.isEmpty() || t.any { q -> it.name.lowercase().contains(q) } }
                val d = pick.firstOrNull { it.battery != null } ?: pick.firstOrNull()
                when {
                    d == null -> TileValue(text = "None", sub = "No headphones connected", state = TileValueState.EMPTY, note = "Nothing connected", icon = "headphones")
                    d.battery == null -> TileValue(text = "On", sub = d.name, icon = "headphones", active = true)
                    else -> TileValue(text = "${d.battery}%", number = d.battery.toDouble(), goal = 100.0, unit = "%", sub = d.name, icon = "headphones", active = true)
                }
            }
        }
    }

    // ── YouTube and Twitch ──────────────────────────────────────────────────

    suspend fun video(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val ep = context.widgetEntryPoint()
        return items.associate { (id, b) ->
            id to runCatching {
                when (b.bind) {
                    "youtube.latest", "youtube.new" -> youtube(context, b)
                    "twitch.live", "twitch.count" -> {
                        val repo = ep.twitchRepository()
                        if (repo.getTrackedChannels().isEmpty()) return@runCatching TileValue.needs("Follow Twitch channels on Home's Twitch card")
                        val live = repo.getLiveStreamsForTrackedChannels().getOrElse { return@runCatching TileValue.error("Couldn't reach Twitch") }
                        val bucket = System.currentTimeMillis() / (5 * 60_000L)
                        if (b.bind == "twitch.count") {
                            TileValue(number = live.size.toDouble(), sub = "live now", icon = "tv", items = live.take(3).map { TileItem(it.userName) })
                        } else if (live.isEmpty()) {
                            TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "Nobody you follow is live")
                        } else {
                            TileValue(
                                number = live.size.toDouble(),
                                url = live.first().channelUrl,
                                items = live.take(b.max ?: 10).map { s ->
                                    TileItem(s.userName, s.title.ifBlank { s.gameName }, compact(s.viewerCount.toLong()), "#9146FF", s.thumbnail(640, 360) + "?t=$bucket", s.channelUrl)
                                },
                            )
                        }
                    }
                    else -> TileValue.error("Unknown source")
                }
            }.getOrElse { TileValue.error(it.message?.take(80) ?: "Couldn't read it") }
        }
    }

    private suspend fun youtube(context: Context, b: TileBlock): TileValue {
        val ep = context.widgetEntryPoint()
        val names = b.query?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().take(6)
        val videos = if (names.isEmpty()) {
            val repo = ep.youTubeRepository()
            if (repo.getTrackedChannels().isEmpty()) return TileValue.needs("Follow YouTube channels on Home's YouTube card, or name one in the widget")
            repo.getLatestVideosForTrackedChannels().getOrElse { return TileValue.error("Couldn't reach YouTube") }
        } else {
            val rss = ep.youTubeRssFeedService()
            coroutineScope {
                names.map { n ->
                    async {
                        val channelId = youtubeChannelId(context, n) ?: return@async emptyList()
                        rss.getLatestVideos(channelId).orEmpty()
                    }
                }.awaitAll().flatten().sortedByDescending { it.publishedAt }
            }
        }
        val now = System.currentTimeMillis()
        fun ms(iso: String) = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
        if (b.bind == "youtube.new") {
            val fresh = videos.count { v -> ms(v.publishedAt)?.let { now - it < 24 * 3_600_000L } == true }
            return TileValue(number = fresh.toDouble(), sub = if (fresh == 1) "new video today" else "new videos today", icon = "youtube", url = "https://www.youtube.com/feed/subscriptions")
        }
        if (videos.isEmpty()) return TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "No videos yet")
        return TileValue(
            number = videos.size.toDouble(),
            url = "https://www.youtube.com/feed/subscriptions",
            icon = "youtube",
            items = videos.distinctBy { it.videoId }.take(b.max ?: 10).map { v ->
                TileItem(
                    v.title,
                    v.channelTitle,
                    ms(v.publishedAt)?.let { ago(it, now) },
                    null,
                    // hqdefault always exists; its 4:3 bars fall outside a 16:9 crop.
                    "https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg",
                    "https://www.youtube.com/watch?v=${v.videoId}",
                )
            },
        )
    }

    /** A channel id for a name, handle or id, looked up once and remembered. */
    private suspend fun youtubeChannelId(context: Context, name: String): String? {
        val n = name.trim()
        if (n.startsWith("UC") && n.length == 24) return n
        Regex("""channel/(UC[\w-]{22})""").find(n)?.let { return it.groupValues[1] }
        val prefs = context.getSharedPreferences("tilesmith_yt", Context.MODE_PRIVATE)
        val key = "id_" + n.lowercase()
        prefs.getString(key, null)?.let { return it }
        context.widgetEntryPoint().youTubeRepository().getTrackedChannels().firstOrNull { it.title.equals(n.removePrefix("@"), ignoreCase = true) }?.let { return it.channelId }
        val found = context.widgetEntryPoint().youTubeRssFeedService().searchChannels(n.removePrefix("@")).getOrNull()?.firstOrNull() ?: return null
        prefs.edit().putString(key, found.channelId).apply()
        return found.channelId
    }

    // ── Coming up ───────────────────────────────────────────────────────────

    suspend fun upcoming(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val ep = context.widgetEntryPoint()
        if (ep.settingsRepository().dashboardServerUrl.value.isBlank()) return items.associate { it.first to TileValue.needs("Set the dashboard server in Settings → Connections") }
        val feed = ep.upcomingRepository().getFeed().getOrNull() ?: ep.upcomingRepository().getCached()
            ?: return items.associate { it.first to TileValue.error("Couldn't reach the dashboard") }
        val now = Instant.now()
        val ahead = feed.events.filter { it.at.isAfter(now.minusSeconds(2 * 3600)) }.sortedBy { it.at }
        fun whenText(at: Instant): String {
            val t = at.atZone(zone)
            val time = t.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            return if (t.toLocalDate() == LocalDate.now(zone)) time else dayWord(t.toLocalDate()) + if (t.hour == 0 && t.minute == 0) "" else " $time"
        }
        return items.associate { (id, b) ->
            id to when (b.bind) {
                "upcoming.list" -> if (ahead.isEmpty()) TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "Nothing coming up") else TileValue(
                    number = ahead.size.toDouble(),
                    items = ahead.take(b.max ?: 10).map { e ->
                        TileItem(e.title, listOf(e.code, e.detail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }, whenText(e.at), e.brand, e.artUrl, e.href)
                    },
                )
                "upcoming.next" -> ahead.firstOrNull { it.at.isAfter(now) }?.let { e ->
                    TileValue(text = e.title, sub = listOf(e.code, e.detail).filter { it.isNotBlank() }.joinToString(" · "), atMs = e.at.toEpochMilli(), image = e.artUrl, url = e.href, icon = "tv")
                } ?: TileValue.empty("Nothing coming up")
                else -> TileValue.error("Unknown source")
            }
        }
    }

    // ── Gmail and Google Tasks ──────────────────────────────────────────────

    suspend fun google(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val ep = context.widgetEntryPoint()
        val access = ep.googleAccess()
        val http = ep.okHttpClient()
        return withContext(Dispatchers.IO) {
            items.associate { (id, b) ->
                id to runCatching {
                    when (b.bind) {
                        "gmail.unread", "gmail.inbox" -> gmail(context, access, http, b)
                        "tasks.list", "tasks.count" -> tasks(access, http, b)
                        else -> TileValue.error("Unknown source")
                    }
                }.getOrElse { TileValue.error(it.message?.take(80) ?: "Couldn't reach Google") }
            }
        }
    }

    private class GoogleAuthGone : Exception("Google needs you to allow it again")

    private suspend fun getJson(access: GoogleAccess, http: OkHttpClient, scope: GoogleScope, url: String): JSONObject {
        repeat(2) { attempt ->
            val token = access.token(scope) ?: throw GoogleAuthGone()
            http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
                if (r.code == 401 && attempt == 0) {
                    access.dropToken(scope)
                    return@repeat
                }
                if (r.code == 403) throw IllegalStateException("Turn on the ${scope.label} API in Google Cloud")
                if (!r.isSuccessful) throw IllegalStateException("${scope.label} said ${r.code}")
                return JSONObject(r.body?.string().orEmpty())
            }
        }
        throw GoogleAuthGone()
    }

    private suspend fun gmail(context: Context, access: GoogleAccess, http: OkHttpClient, b: TileBlock): TileValue {
        if (!access.granted(GoogleScope.GMAIL)) return gmailFromDashboard(context, b)
        val q = listOfNotNull("in:inbox", b.query?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")
        return try {
            if (b.bind == "gmail.unread") {
                val n = if (b.query.isNullOrBlank()) {
                    getJson(access, http, GoogleScope.GMAIL, "$GMAIL/labels/INBOX").optInt("threadsUnread")
                } else {
                    val url = "$GMAIL/threads".toHttpUrl().newBuilder().addQueryParameter("q", "is:unread $q").addQueryParameter("maxResults", "100").build().toString()
                    getJson(access, http, GoogleScope.GMAIL, url).optJSONArray("threads")?.length() ?: 0
                }
                TileValue(number = n.toDouble(), sub = "unread", icon = "mail", url = GMAIL_WEB)
            } else {
                val max = (b.max ?: 12).coerceIn(1, 20)
                val url = "$GMAIL/threads".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("maxResults", max.toString()).build().toString()
                val threads = getJson(access, http, GoogleScope.GMAIL, url).optJSONArray("threads")
                val ids = (0 until (threads?.length() ?: 0)).mapNotNull { threads!!.optJSONObject(it)?.optString("id") }
                val rows = coroutineScope {
                    ids.map { tid ->
                        async(Dispatchers.IO) {
                            runCatching {
                                getJson(access, http, GoogleScope.GMAIL, "$GMAIL/threads/$tid?format=metadata&metadataHeaders=From&metadataHeaders=Subject")
                            }.getOrNull()?.let { gmailRow(context, tid, it) }
                        }
                    }.awaitAll().filterNotNull()
                }
                if (rows.isEmpty()) TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "Inbox zero", url = GMAIL_WEB)
                else TileValue(number = rows.count { it.second }.toDouble(), sub = "unread", icon = "inbox", url = GMAIL_WEB, items = rows.map { it.first })
            }
        } catch (e: GoogleAuthGone) {
            TileValue.needs("Allow Gmail in Settings → Permissions")
        }
    }

    /** One thread as a row (its subject, the latest sender and time), and whether any of it is unread. */
    private fun gmailRow(context: Context, threadId: String, t: JSONObject): Pair<TileItem, Boolean>? {
        val msgs = t.optJSONArray("messages") ?: return null
        if (msgs.length() == 0) return null
        fun header(m: JSONObject, name: String): String? {
            val hs = m.optJSONObject("payload")?.optJSONArray("headers") ?: return null
            return (0 until hs.length()).mapNotNull { hs.optJSONObject(it) }.firstOrNull { it.optString("name").equals(name, true) }?.optString("value")
        }
        val first = msgs.optJSONObject(0)
        val last = msgs.optJSONObject(msgs.length() - 1)
        val unread = (0 until msgs.length()).any { i -> msgs.optJSONObject(i)?.optJSONArray("labelIds")?.let { l -> (0 until l.length()).any { l.optString(it) == "UNREAD" } } == true }
        val from = header(last, "From").orEmpty()
        val name = from.substringBefore('<').trim().trim('"').ifBlank { from.substringAfter('<').substringBefore('@').ifBlank { from } }
        val addr = from.substringAfter('<', from).substringBefore('>').trim()
        val at = last.optString("internalDate").toLongOrNull()
        val subject = header(first, "Subject").orEmpty().ifBlank { "(no subject)" }
        val hue = com.macrotracker.data.dashboard.mailHue(addr).toFloat()
        val mark = if (unread) "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.72f)) and 0xFFFFFF) else null
        val title = if (msgs.length() > 1) "$name (${msgs.length()})" else name
        return TileItem(title, subject, at?.let { ago(it) }, mark, link = "$GMAIL_WEB#inbox/$threadId") to unread
    }

    /** The dashboard's copy of the inbox, for a phone that hasn't signed in to Google. */
    private suspend fun gmailFromDashboard(context: Context, b: TileBlock): TileValue {
        val ep = context.widgetEntryPoint()
        if (!b.query.isNullOrBlank() || ep.settingsRepository().dashboardServerUrl.value.isBlank()) return TileValue.needs("Allow Gmail in Settings → Permissions")
        val repo = ep.dashboardRepository()
        val m = runCatching { repo.mail() }.getOrNull() ?: repo.cachedMail() ?: return TileValue.needs("Allow Gmail in Settings → Permissions")
        if (b.bind == "gmail.unread") return TileValue(number = m.unread.toDouble(), sub = "unread", icon = "mail", url = GMAIL_WEB)
        val rows = m.listedInbox()
        return TileValue(
            number = m.unread.toDouble(),
            sub = "unread",
            icon = "inbox",
            url = GMAIL_WEB,
            items = rows.take(b.max ?: 12).map { r ->
                val hue = com.macrotracker.data.dashboard.mailHue(r.addr).toFloat()
                TileItem(r.from, r.subject, r.at?.let { ago(it.toEpochMilli()) }, "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.72f)) and 0xFFFFFF), link = GMAIL_WEB)
            },
        )
    }

    private suspend fun tasks(access: GoogleAccess, http: OkHttpClient, b: TileBlock): TileValue {
        if (!access.granted(GoogleScope.TASKS)) return TileValue.needs("Allow Google Tasks in Settings → Permissions")
        return try {
            val lists = getJson(access, http, GoogleScope.TASKS, "$TASKS/users/@me/lists?maxResults=100").optJSONArray("items") ?: JSONArray()
            val all = (0 until lists.length()).mapNotNull { lists.optJSONObject(it) }
            val t = terms(b.query)
            val picked = if (t.isEmpty()) all else all.filter { l -> t.any { l.optString("title").lowercase().contains(it) } }
            if (picked.isEmpty()) return TileValue.empty("No list called ${b.query}")
            val today = LocalDate.now(zone)
            data class T(val title: String, val due: LocalDate?, val link: String?)
            val open = picked.take(6).flatMap { l ->
                val js = getJson(access, http, GoogleScope.TASKS, "$TASKS/lists/${l.optString("id")}/tasks?showCompleted=false&showHidden=false&maxResults=100").optJSONArray("items") ?: JSONArray()
                (0 until js.length()).mapNotNull { js.optJSONObject(it) }.filter { it.optString("status") != "completed" && it.optString("title").isNotBlank() }.map { o ->
                    // Tasks keeps a due day at midnight UTC: its date part is the day.
                    T(o.optString("title"), o.optString("due").takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }, o.optString("webViewLink").ifBlank { null })
                }
            }.sortedWith(compareBy<T> { it.due == null }.thenBy { it.due?.toEpochDay() ?: Long.MAX_VALUE })
            val dueNow = open.count { it.due != null && !it.due.isAfter(today) }
            if (b.bind == "tasks.count") {
                TileValue(number = open.size.toDouble(), sub = if (dueNow > 0) "$dueNow due today" else "open", icon = "tasks", url = TASKS_WEB)
            } else if (open.isEmpty()) {
                TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "All done", url = TASKS_WEB)
            } else {
                TileValue(
                    number = open.size.toDouble(),
                    url = TASKS_WEB,
                    items = open.take(b.max ?: 8).map { x ->
                        val late = x.due != null && x.due.isBefore(today)
                        TileItem(x.title, null, x.due?.let { if (late) "Overdue" else dayWord(it) }, if (late) "#E34671" else if (x.due == today) "#F1B467" else null, link = x.link ?: TASKS_WEB)
                    },
                )
            }
        } catch (e: GoogleAuthGone) {
            TileValue.needs("Allow Google Tasks in Settings → Permissions")
        }
    }

    private const val GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me"
    private const val GMAIL_WEB = "https://mail.google.com/mail/u/0/"
    private const val TASKS = "https://tasks.googleapis.com/tasks/v1"
    private const val TASKS_WEB = "https://tasks.google.com/"

    // ── Health extras ───────────────────────────────────────────────────────

    suspend fun healthExtras(context: Context, items: List<Pair<String, TileBlock>>): Map<String, TileValue> {
        val hc = context.widgetEntryPoint().healthConnectRepository()
        val out = HashMap<String, TileValue>()
        items.filter { it.second.bind == "health.workouts" }.forEach { (id, b) ->
            out[id] = runCatching {
                val list = hc.readRecentActivities(days = 21, limit = b.max ?: 6)
                if (list.isEmpty()) TileValue(items = emptyList(), state = TileValueState.EMPTY, note = "No workouts lately")
                else TileValue(
                    number = list.size.toDouble(),
                    items = list.map { a ->
                        val start = a.startTime.atZone(zone)
                        val day = dayWord(start.toLocalDate()).let { if (it == "Today") "Today ${start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}" else it }
                        val end = a.distanceKm?.takeIf { it >= 0.1 }?.let { "%.1f km".format(it) } ?: "${a.duration.toMinutes()} min"
                        TileItem(a.typeLabel.ifBlank { a.title }, day, end)
                    },
                )
            }.getOrElse { TileValue.error("Health Connect didn't answer") }
        }
        val vitals = items.filter { it.second.bind == "health.weight" || it.second.bind == "health.hrv" }
        if (vitals.isNotEmpty()) {
            val v = runCatching { hc.readBodyVitals(days = 60) }.getOrNull()
            vitals.forEach { (id, b) ->
                out[id] = when {
                    v == null -> TileValue.error("Health Connect didn't answer")
                    b.bind == "health.weight" -> v.weightKg.sortedBy { it.time }.let { w ->
                        val last = w.lastOrNull() ?: return@let TileValue.empty("No weight logged")
                        val monthAgo = w.lastOrNull { it.time.isBefore(Instant.now().minusSeconds(30L * 86_400)) } ?: w.first()
                        val diff = last.value - monthAgo.value
                        TileValue(
                            number = (last.value * 10).roundToInt() / 10.0,
                            unit = "kg",
                            sub = if (abs(diff) < 0.05) "Steady" else "${if (diff > 0) "+" else "−"}${"%.1f".format(abs(diff))} in 30 days",
                            icon = "scale",
                            series = w.takeLast(12).map { it.value.toFloat() },
                        )
                    }
                    else -> v.hrvMs.maxByOrNull { it.time }?.let { s ->
                        TileValue(number = s.value.roundToInt().toDouble(), unit = "ms", sub = "HRV", icon = "heart_pulse", series = v.hrvMs.sortedBy { it.time }.takeLast(14).map { it.value.toFloat() })
                    } ?: TileValue.empty("No HRV readings")
                }
            }
        }
        return out
    }
}
