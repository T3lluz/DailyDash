package com.macrotracker.data.link

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a shortcut does on the laptop. */
enum class LinkShortcutKind(val id: String) {
    /** Opens one of the laptop's apps, by its desktop id. */
    APP("app"),
    /** Opens a web page in the laptop's browser. */
    SITE("site"),
    /** Presses a key with its modifiers: one of the laptop's own bindings, or one made here. */
    KEYS("keys"),
    /** Types some words. */
    TEXT("text"),
    ;

    companion object {
        fun of(id: String?): LinkShortcutKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A button of the remote that does one thing on the laptop. [key] is a name the laptop's
 * injector knows (`w`, `enter`, `#193`); [cap] is what that key reads as, for its keycap.
 */
data class LinkShortcut(
    val id: String = UUID.randomUUID().toString(),
    val kind: LinkShortcutKind,
    val label: String,
    val app: String = "",
    val url: String = "",
    val key: String = "",
    val mods: List<String> = emptyList(),
    val cap: String = "",
    val text: String = "",
) {
    /** Whether it has what its kind needs to do anything. */
    val complete: Boolean
        get() = label.isNotBlank() && when (kind) {
            LinkShortcutKind.APP -> app.isNotBlank()
            LinkShortcutKind.SITE -> url.startsWith("http://") || url.startsWith("https://")
            LinkShortcutKind.KEYS -> key.isNotBlank()
            LinkShortcutKind.TEXT -> text.isNotEmpty()
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.id).put("label", label)
        .put("app", app).put("url", url).put("key", key).put("mods", JSONArray(mods)).put("cap", cap).put("text", text)

    companion object {
        fun fromJson(o: JSONObject): LinkShortcut? {
            val kind = LinkShortcutKind.of(o.optString("kind")) ?: return null
            return LinkShortcut(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                kind = kind,
                label = o.optString("label"),
                app = o.optString("app"),
                url = o.optString("url"),
                key = o.optString("key"),
                mods = o.optJSONArray("mods")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it in LinkKeys.MODS },
                cap = o.optString("cap"),
                text = o.optString("text"),
            ).takeIf { it.complete }
        }
    }
}

/**
 * One of the laptop's apps, as its launcher lists it. [bitmap] is its icon, decoded off the main
 * thread; [hasIcon] says the laptop has one, which may still be on its way when [png] is null.
 */
class LinkApp(val id: String, val name: String, val png: ByteArray? = null, val bitmap: Bitmap? = null, val hasIcon: Boolean = png != null)

/** One of the laptop's key bindings, by what it does: "Switch to workspace 3", Super + 3. */
data class LinkBind(val label: String, val key: String, val mods: List<String>, val cap: String)

/** What the laptop offers to make a shortcut of; [iconsDone] once the last batch of icons has come. */
class LinkCatalog(val apps: List<LinkApp>, val binds: List<LinkBind>, val at: Long = System.currentTimeMillis(), val iconsDone: Boolean = false)

/**
 * Keys by the names the laptop's injector knows (tandem/inject.py), with what each reads as.
 * Only keys that sit in the same place whatever the laptop's layout are offered for a key made
 * here; a binding read from the laptop carries its own keycap.
 */
object LinkKeys {
    /** In the order Omarchy writes them: Super first. */
    val MODS = listOf("super", "ctrl", "alt", "shift")

    /** The keys a combination made here can end in, in the order they are offered. */
    val SPECIAL = listOf(
        "enter", "space", "tab", "esc", "backspace", "delete", "left", "up", "down", "right",
        "home", "end", "pageup", "pagedown", "print",
    ) + (1..12).map { "f$it" } + listOf("play", "next", "prev", "mute", "voldown", "volup")

    private val CAPS = mapOf(
        "enter" to "Enter", "space" to "Space", "tab" to "Tab", "esc" to "Esc", "backspace" to "Backspace",
        "delete" to "Del", "insert" to "Ins", "home" to "Home", "end" to "End", "pageup" to "PgUp", "pagedown" to "PgDn",
        "left" to "←", "right" to "→", "up" to "↑", "down" to "↓", "print" to "Print", "comma" to ",", "dot" to ".",
        "minus" to "−", "equal" to "=", "volup" to "Vol +", "voldown" to "Vol −", "mute" to "Mute", "play" to "Play",
        "next" to "Next", "prev" to "Prev", "brightup" to "Bright +", "brightdown" to "Bright −",
    )

    fun cap(key: String): String = CAPS[key] ?: when {
        key.length == 1 -> key.uppercase()
        key.matches(Regex("f\\d{1,2}")) -> key.uppercase()
        else -> key.replaceFirstChar { it.uppercase() }
    }

    fun modCap(mod: String): String = when (mod) {
        "ctrl" -> "Ctrl"
        "alt" -> "Alt"
        "shift" -> "Shift"
        "super" -> "Super"
        else -> mod
    }

    /** A key typed into the field: a letter or a digit, the rest by name. */
    fun fromTyped(text: String): String? {
        val c = text.trim().lastOrNull()?.lowercaseChar() ?: return null
        return when {
            c in 'a'..'z' || c in '0'..'9' -> c.toString()
            else -> null
        }
    }

    /**
     * Whether a key made here can be pressed: a letter, a digit or a named key. A raw code (`#193`)
     * never is: only one of the laptop's own bindings carries one, and the laptop checks it again.
     */
    fun pressable(key: String): Boolean =
        key.length == 1 && (key[0] in 'a'..'z' || key[0] in '0'..'9') || key in SPECIAL

    /** Modifiers in the order a keycap row reads them. */
    fun ordered(mods: Collection<String>): List<String> = MODS.filter { it in mods }

    /** "Super + Shift + W", for a name a combination is given until it gets a better one. */
    fun combo(mods: Collection<String>, cap: String): String =
        (ordered(mods).map(::modCap) + cap).joinToString(" + ")
}

/** "youtube.com" is https://youtube.com; anything that isn't a web address is null. */
fun normalizeShortcutUrl(typed: String): String? {
    val t = typed.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    val host = runCatching { URI(withScheme).host }.getOrNull() ?: return null
    if (!host.contains('.') && host != "localhost") return null
    return withScheme
}

/** "https://www.youtube.com/feed" is "Youtube": a name for a site until a better one is typed. */
fun siteName(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.").removePrefix("m.")
    val parts = host.split('.').filter { it.isNotBlank() }
    val main = when {
        parts.size >= 3 && parts[parts.size - 2].length <= 3 -> parts[parts.size - 3]   // bbc.co.uk
        parts.size >= 2 -> parts[parts.size - 2]
        else -> host
    }
    return main.replaceFirstChar { it.uppercase() }
}

/**
 * What an AI answered to "make a shortcut that …", as a shortcut the remote can save, or why
 * not. Apps must be ones the laptop has and keys ones it can press; a site gets https.
 */
sealed interface ShortcutAnswer {
    /** [reply] is what the AI says about it, one sentence (blank when it said nothing). */
    data class Made(val shortcut: LinkShortcut, val reply: String = "") : ShortcutAnswer
    /** [suggestions] are asks a button can do that come close, for the sheet to offer. */
    data class Refused(val reason: String, val suggestions: List<String> = emptyList()) : ShortcutAnswer
}

/** One ask in the sheet and what the AI said back, so a follow-up ("make it 4") has something to change. */
data class ShortcutTurn(val ask: String, val reply: String)

/** A line from the AI, on one line and short. */
private fun oneLine(text: String, max: Int): String =
    text.lines().joinToString(" ") { it.trim() }.trim().let { if (it.length <= max) it else it.take(max - 1).trimEnd() + "…" }

fun parseShortcutAnswer(raw: String, catalog: LinkCatalog?): ShortcutAnswer {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return ShortcutAnswer.Refused("The AI didn't answer with a shortcut.")
    val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
        ?: return ShortcutAnswer.Refused("The AI didn't answer with a shortcut.")
    o.optString("error").takeIf { it.isNotBlank() }?.let { why ->
        val ideas = o.optJSONArray("suggestions")?.let { a -> (0 until a.length()).map { oneLine(a.optString(it), 40) } }.orEmpty()
            .filter { it.isNotBlank() }.distinct().take(3)
        return ShortcutAnswer.Refused(oneLine(why, 160), ideas)
    }
    val kind = LinkShortcutKind.of(o.optString("kind").lowercase())
        ?: return ShortcutAnswer.Refused("The AI didn't say what kind of shortcut it is.")
    val label = o.optString("label").trim().take(LABEL_MAX)
    val shortcut = when (kind) {
        LinkShortcutKind.APP -> {
            val wanted = o.optString("app").trim()
            val app = catalog?.apps?.let { apps ->
                apps.firstOrNull { it.id == wanted } ?: apps.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
            } ?: return ShortcutAnswer.Refused("That app isn't on the laptop.")
            LinkShortcut(kind = kind, label = label.ifBlank { app.name }, app = app.id)
        }
        LinkShortcutKind.SITE -> {
            val url = normalizeShortcutUrl(o.optString("url")) ?: return ShortcutAnswer.Refused("The AI's address isn't a web page.")
            LinkShortcut(kind = kind, label = label.ifBlank { siteName(url) }, url = url)
        }
        LinkShortcutKind.KEYS -> {
            val key = o.optString("key").trim().lowercase()
            val mods = o.optJSONArray("mods")?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() } }.orEmpty()
                .map { if (it == "control") "ctrl" else if (it == "win" || it == "meta" || it == "mod") "super" else it }
            if (mods.any { it !in LinkKeys.MODS }) return ShortcutAnswer.Refused("The AI picked a key the laptop can't press.")
            // the laptop's own binding, when it is one: its keycap reads as the laptop's keyboard does;
            // a raw code is only ever one of those
            val bind = catalog?.binds?.firstOrNull { it.key == key && it.mods.toSet() == mods.toSet() }
            if (bind == null && !LinkKeys.pressable(key)) return ShortcutAnswer.Refused("The AI picked a key the laptop can't press.")
            val cap = bind?.cap ?: LinkKeys.cap(key)
            LinkShortcut(
                kind = kind, label = label.ifBlank { bind?.label?.let(::bindName) ?: LinkKeys.combo(mods, cap) },
                key = key, mods = LinkKeys.ordered(mods), cap = cap,
            )
        }
        LinkShortcutKind.TEXT -> {
            // one line: a newline is Enter, and in a terminal that would run what came before it
            val text = o.optString("text").lines().joinToString(" ") { it.trim() }.trim().take(500)
            if (text.isEmpty()) return ShortcutAnswer.Refused("The AI gave nothing to type.")
            LinkShortcut(kind = kind, label = label.ifBlank { text.take(LABEL_MAX) }, text = text)
        }
    }
    return if (shortcut.complete) {
        ShortcutAnswer.Made(shortcut, oneLine(o.optString("reply"), 160))
    } else {
        ShortcutAnswer.Refused("The AI's shortcut was missing something.")
    }
}

private val KIND_SO_FAR = Regex(""""kind"\s*:\s*"([a-z]+)"""")

/**
 * What a shortcut answer still streaming in says the AI is doing: which kind it settled on, then
 * writing back. Null before it says anything worth showing.
 */
fun shortcutProgress(partial: CharSequence): String? {
    val text = partial.toString()
    if (text.contains("\"error\"") || text.contains("\"reply\"")) return "Writing back"
    return when (LinkShortcutKind.of(KIND_SO_FAR.find(text)?.groupValues?.get(1))) {
        LinkShortcutKind.KEYS -> "Picking the keys"
        LinkShortcutKind.APP -> "Picking the app"
        LinkShortcutKind.SITE -> "Picking the page"
        LinkShortcutKind.TEXT -> "Writing the words"
        null -> null
    }
}

/**
 * What a shortcut does, in words: "Opens Spotify on t3lluz". Keys read as their combination; the
 * sheet draws them as keycaps. Null while it doesn't do anything yet.
 */
fun describeShortcut(s: LinkShortcut, laptop: String, appName: String? = null): String? = when (s.kind) {
    LinkShortcutKind.APP -> s.app.takeIf { it.isNotBlank() }?.let { "Opens ${appName ?: s.label.ifBlank { it }} on $laptop" }
    LinkShortcutKind.SITE -> normalizeShortcutUrl(s.url)?.let { url ->
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
        "Opens $host in $laptop's browser"
    }
    LinkShortcutKind.KEYS -> s.key.takeIf { it.isNotBlank() }?.let { "Presses ${LinkKeys.combo(s.mods, s.cap.ifBlank { LinkKeys.cap(it) })} on $laptop" }
    LinkShortcutKind.TEXT -> s.text.takeIf { it.isNotEmpty() }?.let { "Types \"${oneLine(it, 40)}\" on $laptop" }
}

/**
 * A binding's description as a button's name: "Switch to workspace 3" is "Workspace 3". The
 * rest are kept as they are; the name can be changed in the sheet.
 */
fun bindName(label: String): String {
    val t = label.trim()
    for (lead in listOf("Switch to ", "Go to ", "Open ")) {
        if (t.startsWith(lead, ignoreCase = true) && t.length > lead.length) {
            return t.substring(lead.length).replaceFirstChar { it.uppercase() }
        }
    }
    return t
}

/** How long a shortcut's name may be: about what fits under its icon. */
const val LABEL_MAX = 24

/**
 * The remote's shortcuts, in the order they stand, and their pictures: an app's icon as the
 * laptop sent it, a site's own icon, fetched once. Kept on this phone.
 */
@Singleton
class LinkShortcutStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences("link_shortcuts", Context.MODE_PRIVATE)
    private val http = okHttpClient.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    private val dir by lazy { File(context.filesDir, "link_icons").apply { mkdirs() } }

    private val _shortcuts = MutableStateFlow(read())
    val shortcuts: StateFlow<List<LinkShortcut>> = _shortcuts

    /** Bumps when a picture lands, so tiles drawn before it read their file again. */
    private val _icons = MutableStateFlow(0)
    val icons: StateFlow<Int> = _icons

    private fun read(): List<LinkShortcut> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(LinkShortcut::fromJson) }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<LinkShortcut>) {
        _shortcuts.value = list
        prefs.edit { putString(KEY, JSONArray(list.map { it.toJson() }).toString()) }
    }

    /** Adds [s], or puts it in place of the one with its id. */
    fun save(s: LinkShortcut) {
        val list = _shortcuts.value
        write(if (list.any { it.id == s.id }) list.map { if (it.id == s.id) s else it } else list + s)
    }

    fun remove(id: String) = write(_shortcuts.value.filterNot { it.id == id })

    /** Puts the shortcuts in the order of [ids]; any not named keep their place at the end. */
    fun setOrder(ids: List<String>) {
        val list = _shortcuts.value
        val byId = list.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] } + list.filter { it.id !in ids }
        if (ordered != list) write(ordered)
    }

    /** The picture a tile shows, if there is one on disk. */
    fun iconFile(s: LinkShortcut): File? = when (s.kind) {
        LinkShortcutKind.APP -> File(dir, "app-${hash(s.app)}.png")
        LinkShortcutKind.SITE -> host(s.url)?.let { File(dir, "site-${hash(it)}.png") }
        else -> null
    }?.takeIf { it.isFile }

    /**
     * Keeps an app's icon as the laptop sent it. A null [png] means the laptop said it has none
     * (never "not here yet"): that is noted, so it isn't asked again.
     */
    suspend fun keepAppIcon(appId: String, png: ByteArray?) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (png != null) File(dir, "app-${hash(appId)}.png").writeBytes(png) else File(dir, "app-${hash(appId)}.none").createNewFile()
            }
        }
        if (png != null) _icons.value++
    }

    /** Whether a picture for [s] was already looked for and there is none to have. */
    fun noIcon(s: LinkShortcut): Boolean = s.kind == LinkShortcutKind.APP && File(dir, "app-${hash(s.app)}.none").isFile

    /**
     * Fetches a site's icon once: the biggest its page names (the one made for phones'
     * home screens first), else the usual places. A picture this can't read leaves the globe.
     * Returns the name the site gives itself (`og:site_name`, "YouTube"), when it gives one.
     */
    suspend fun fetchSiteIcon(url: String): String? {
        val host = host(url) ?: return null
        val file = File(dir, "site-${hash(host)}.png")
        val origin = runCatching { URI(url).let { "${it.scheme}://${it.host}" } }.getOrNull() ?: return null
        val head = withContext(Dispatchers.IO) { runCatching { pageHead(origin) }.getOrNull() }
        if (file.isFile) return head?.let(::ownName)
        val bitmap = withContext(Dispatchers.IO) {
            val named = head?.let { runCatching { pageIcons(origin, it) }.getOrNull() }.orEmpty()
            (named + listOf("$origin/apple-touch-icon.png", "$origin/favicon.ico")).distinct().asSequence()
                .mapNotNull { runCatching { picture(it) }.getOrNull() }
                .firstOrNull()
        }
        if (bitmap != null) {
            withContext(Dispatchers.IO) {
                runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            _icons.value++
        }
        return head?.let(::ownName)
    }

    /** The start of a page, up to its `</head>`: the icons and its name are near the top, and a page can run to a megabyte. */
    private fun pageHead(origin: String): String? =
        get(origin, 300_000, keepStart = true)?.toString(Charsets.UTF_8)?.substringBefore("</head>")

    /** The name a page gives its site, if it is short enough for a tile. */
    private fun ownName(head: String): String? {
        val meta = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(head)
            .firstOrNull { (attr(it.value, "property") ?: attr(it.value, "name")).equals("og:site_name", ignoreCase = true) }
        return meta?.let { attr(it.value, "content") }?.trim()?.replace("&amp;", "&")?.takeIf { it.isNotBlank() && it.length <= 18 }
    }

    /** The icons a page's head names, biggest first, apple-touch-icon before the rest at the same size. */
    private fun pageIcons(origin: String, head: String): List<String> {
        return Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(head).mapNotNull { m ->
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: return@mapNotNull null
            if ("icon" !in rel || "mask" in rel) return@mapNotNull null
            val href = attr(tag, "href") ?: return@mapNotNull null
            if (href.endsWith(".svg", ignoreCase = true)) return@mapNotNull null
            val size = attr(tag, "sizes")?.substringBefore('x')?.toIntOrNull() ?: if ("apple" in rel) 180 else 32
            val abs = runCatching { URI(origin + "/").resolve(href.replace("&amp;", "&")).toString() }.getOrNull() ?: return@mapNotNull null
            Triple(abs, size, "apple" in rel)
        }.sortedWith(compareByDescending<Triple<String, Int, Boolean>> { it.second }.thenByDescending { it.third })
            .map { it.first }.toList()
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag)?.let { m ->
            m.groupValues.drop(2).firstOrNull { it.isNotEmpty() }
        }

    private fun picture(url: String): Bitmap? {
        val bytes = get(url, 600_000) ?: return null
        // its size first: a small file can claim a picture big enough to run the phone out of memory
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 192 && bounds.outHeight / (sample * 2) >= 192) sample *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // a 16 px favicon would be a smudge on a tile; better the globe
        return b.takeIf { it.width >= 24 && it.height >= 24 }?.let {
            if (it.width > 192) Bitmap.createScaledBitmap(it, 192, 192 * it.height / it.width, true) else it
        }
    }

    /** Up to [limit] bytes of [url]; past that, nothing, or with [keepStart] the first [limit]. */
    private fun get(url: String, limit: Int, keepStart: Boolean = false): ByteArray? {
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            val body = r.body?.byteStream() ?: return null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = body.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > limit) return if (keepStart) out.toByteArray().copyOf(limit) else null
            }
            return out.toByteArray()
        }
    }

    private fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private companion object {
        const val KEY = "list"
        // a desktop browser's name: sites hand phones a mobile page that lists only a tiny favicon
        const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
    }
}
