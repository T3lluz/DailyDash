package com.macrotracker.data.hermes

import kotlin.math.abs

/**
 * The web panel's model and mode rules (`hermes-models.js`, `hermes-core.js`), ported so the
 * phone offers exactly what the desk does: every family from every brain the server is
 * signed in to, the depth, thinking, fast and context modifiers within a family, and the
 * modes the current brain's own CLI offers.
 */
object HermesCatalog {

    /** Depths, shallow to deep. Extra high and Max are two depths, not one. */
    val EFFORTS = listOf("off", "min", "low", "med", "high", "xhigh", "max")

    val EFFORT_LABEL = mapOf(
        "off" to "None", "min" to "Min", "low" to "Low", "med" to "Medium",
        "high" to "High", "xhigh" to "Extra", "max" to "Max",
    )

    val EFFORT_NOTE = mapOf(
        "off" to "No reasoning at all. Fastest, shallowest",
        "min" to "A moment's thought",
        "low" to "A little thought before it answers",
        "med" to "The everyday middle",
        "high" to "Thinks properly. The usual default",
        "xhigh" to "Thinks harder, and takes longer",
        "max" to "As deep as this model goes",
    )

    /** What Hermes' own brain gets when no CLI supplies modes. */
    val KIND_FALLBACK = listOf(
        HermesMode("plan", "Plan", "read", "reads, does not change", "Read-only. Looks around and reports. A change is described, not applied."),
        HermesMode("ask", "Ask", "ask", "asks before it changes anything", "Reads by itself. A change comes back as an approval card."),
        HermesMode("agent", "Agent", "write", "changes things on its own", "Reversible changes run themselves. Deletes, the proxy and a reboot still ask."),
    )

    private val PERM_KIND = mapOf("none" to "chat", "read" to "read", "ask" to "ask", "full" to "write")
    private val GROUP_ORDER = listOf("Hermes' own", "Grok Bot", "Claude", "Cursor", "OpenCode")

    /** One family in the picker. */
    data class Family(
        val key: String,
        val label: String,
        val group: String,
        val note: String?,
        val current: Boolean,
        /** OpenCode's billed rows, which Claude and Cursor already cover; shown only when searched for. */
        val hidden: Boolean,
        val members: List<HermesModelOption>,
    )

    /** The current model's family and every modifier its connection offers. */
    data class Modifiers(
        val current: HermesModelOption?,
        val members: List<HermesModelOption>,
        val efforts: List<String>,
        /** The family also has a row with no depth: whatever that CLI does by default. */
        val hasBase: Boolean,
        val think: Boolean,
        val fast: Boolean,
        val contexts: List<Int>,
        val window: Int,
    ) {
        /** Worth a chip on the composer. */
        val any: Boolean get() = current != null && (efforts.size > 1 || think || fast || contexts.size > 1)
    }

    fun current(status: HermesStatus?): HermesModelOption? =
        status?.models?.firstOrNull { it.current } ?: status?.models?.firstOrNull { it.id == status.model }

    fun byId(status: HermesStatus?, id: String): HermesModelOption? = status?.models?.firstOrNull { it.id == id }

    fun sourceOf(model: HermesModelOption?): String {
        if (model == null) return ""
        if (model.source.isNotBlank()) return model.source
        if (model.id.startsWith("own:")) return ""
        return model.id.substringBefore(':', "")
    }

    /** The families, grouped and ranked the way the web lists them. */
    fun families(status: HermesStatus?): List<Family> {
        val models = status?.models.orEmpty()
        val hasClaude = models.any { it.group == "Claude" }
        val hasCursor = models.any { it.group == "Cursor" }
        val grouped = LinkedHashMap<String, MutableList<HermesModelOption>>()
        for (m in models) grouped.getOrPut(m.family.ifBlank { m.id }) { mutableListOf() } += m
        return grouped.map { (key, sibs) ->
            val first = sibs.first()
            Family(
                key = key,
                label = prettyLabel(first.familyLabel.ifBlank { first.label }, first.group),
                group = first.group,
                note = modNote(sibs) ?: first.note,
                current = sibs.any { it.current },
                hidden = first.group == "OpenCode" && sibs.none { it.free },
                members = sibs,
            )
        }
            .filter { it.group != "OpenCode" || !isDupOpenCode(it.key, hasClaude, hasCursor) }
            .sortedWith(
                compareBy<Family> { groupRank(it.group) }
                    .thenBy { !it.current }
                    .thenBy { famRank(it.label) }
                    .thenBy(NaturalOrder) { it.label },
            )
    }

    fun modifiers(status: HermesStatus?): Modifiers {
        val m = current(status)
            ?: return Modifiers(null, emptyList(), emptyList(), false, false, false, emptyList(), 0)
        val sibs = status?.models.orEmpty().filter { it.family == m.family }.ifEmpty { listOf(m) }
        val contexts = m.contexts.filter { it > 0 }.distinct().sorted()
        return Modifiers(
            current = m,
            members = sibs,
            efforts = EFFORTS.filter { e -> sibs.any { it.effort == e } },
            hasBase = sibs.any { it.effort.isBlank() },
            think = sibs.any { it.think } && sibs.any { !it.think },
            fast = sibs.any { it.fast } && sibs.any { !it.fast },
            contexts = contexts,
            window = windowOf(m, status?.window ?: 0),
        )
    }

    /** The last window asked for when this family has it, otherwise the model's own default. */
    fun windowOf(m: HermesModelOption?, preferred: Int): Int {
        val opts = m?.contexts.orEmpty()
        if (preferred > 0 && preferred in opts) return preferred
        return m?.ctxDefault?.takeIf { it > 0 } ?: opts.firstOrNull() ?: 0
    }

    /**
     * The row in [members] closest to the modifiers asked for: an exact hit when there is
     * one, otherwise the nearest depth with thinking and fast kept.
     */
    fun pickVariant(members: List<HermesModelOption>, effort: String, think: Boolean, fast: Boolean): HermesModelOption? {
        fun idx(e: String) = EFFORTS.indexOf(e)
        fun score(x: HermesModelOption): Int =
            (if (x.think != think) 100 else 0) +
                (if (x.fast != fast) 10 else 0) +
                when {
                    x.effort == effort -> 0
                    idx(x.effort) < 0 || idx(effort) < 0 -> 5
                    else -> abs(idx(x.effort) - idx(effort))
                }
        return members.minByOrNull(::score)
    }

    /** Same family, one modifier changed, the rest kept. */
    fun variant(mods: Modifiers, effort: String? = null, think: Boolean? = null, fast: Boolean? = null): HermesModelOption? {
        val cur = mods.current ?: return null
        return pickVariant(mods.members, effort ?: cur.effort, think ?: cur.think, fast ?: cur.fast)
    }

    /** The modes the current brain's CLI offers, or the plain three for Hermes' own. */
    fun modes(status: HermesStatus?, model: HermesModelOption? = current(status)): List<HermesMode> {
        val source = sourceOf(model)
        val got = status?.modes?.get(source).orEmpty()
        return got.ifEmpty { KIND_FALLBACK }
    }

    /**
     * What a thread's saved mode means under the current brain: the same id when it offers
     * it, otherwise the mode of the same kind (Cursor's Agent becomes Claude's Accept edits).
     */
    fun modeFor(status: HermesStatus?, id: String, model: HermesModelOption? = current(status)): HermesMode {
        val modes = modes(status, model)
        modes.firstOrNull { it.id == id }?.let { return it }
        val was = status?.modes?.values?.flatten()?.firstOrNull { it.id == id }
        val kind = was?.kind ?: PERM_KIND[id] ?: HermesPermission.fromId(id).let { p ->
            when (p) {
                HermesPermission.CHAT -> "chat"
                HermesPermission.LOOK -> "read"
                HermesPermission.ASK -> "ask"
                HermesPermission.FULL -> "write"
            }
        }
        return modes.firstOrNull { it.kind == kind }
            ?: modes.firstOrNull { it.kind == "ask" }
            ?: modes.first()
    }

    /** `128000` → `128k`, a million → `1M`. */
    fun ctxLabel(n: Int): String = when {
        n <= 0 -> ""
        n >= 950_000 -> "1M"
        else -> "${(n + 500) / 1000}k"
    }

    /** OpenCode ships raw ids; `claude-opus-4-5` reads as Claude Opus 4.5. */
    fun prettyLabel(label: String, group: String): String {
        if (group != "OpenCode") return label
        var s = label.substringAfter('/')
        s = s.replace(Regex("""(\d)-(\d)"""), "$1.$2").replace(Regex("[-_]+"), " ")
        s = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        return s.replace(Regex("""\bGpt\b""", RegexOption.IGNORE_CASE), "GPT")
            .replace(Regex("""\bGlm\b"""), "GLM")
            .replace(Regex("""\bAi\b"""), "AI")
    }

    /** What a family offers beyond its name, in words. */
    private fun modNote(sibs: List<HermesModelOption>): String? {
        val bits = mutableListOf<String>()
        if (sibs.any { it.free }) bits += "free"
        val eff = EFFORTS.filter { e -> sibs.any { it.effort == e } }
        if (eff.size > 1) bits += "${eff.size} depths"
        if (sibs.any { it.think } && sibs.any { !it.think }) bits += "thinking" else if (sibs.all { it.think }) bits += "thinks"
        if (sibs.any { it.fast }) bits += "fast"
        val c0 = sibs.first()
        val lo = c0.ctxDefault
        val hi = c0.ctxMax
        when {
            lo > 0 && hi > 0 && lo != hi -> bits += "${ctxLabel(lo)} / ${ctxLabel(hi)}"
            hi > 0 || lo > 0 -> bits += "${ctxLabel(if (hi > 0) hi else lo)} context"
        }
        return bits.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    private fun groupRank(group: String): Int = GROUP_ORDER.indexOf(group).let { if (it < 0) 40 else it }

    /** Within a provider: the everyday picks first, then A–Z. */
    private fun famRank(label: String): Int {
        val l = label.lowercase()
        return when {
            l == "auto" -> 0
            "composer" in l -> 1
            Regex("""\bopus\b""").containsMatchIn(l) -> 2
            Regex("""\bsonnet\b""").containsMatchIn(l) -> 3
            Regex("""\bgrok\b""").containsMatchIn(l) -> 4
            Regex("""\bgpt|codex|sol|luna\b""").containsMatchIn(l) -> 5
            Regex("""\bgemini\b""").containsMatchIn(l) -> 6
            Regex("""\bhaiku\b""").containsMatchIn(l) -> 7
            else -> 20
        }
    }

    /** OpenCode also lists the frontier models Claude and Cursor already sign this machine in to. */
    private fun isDupOpenCode(key: String, hasClaude: Boolean, hasCursor: Boolean): Boolean {
        val id = key.lowercase()
        if (hasClaude && Regex("anthropic|claude").containsMatchIn(id)) return true
        if (hasCursor && Regex("""(openai/|google/|xai/|cursor/|/gpt|/gemini|/grok|/codex|composer)""").containsMatchIn(id)) return true
        return false
    }

    /** "Model 10" after "Model 9". */
    private object NaturalOrder : Comparator<String> {
        private val chunk = Regex("""\d+|\D+""")
        override fun compare(a: String, b: String): Int {
            val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
            val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(x.size, y.size)) {
                val p = x[i]
                val q = y[i]
                val c = if (p[0].isDigit() && q[0].isDigit()) {
                    (p.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(q.toBigIntegerOrNull() ?: 0.toBigInteger())
                } else {
                    p.compareTo(q)
                }
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }
}
