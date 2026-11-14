package com.macrotracker.data.hermes

/**
 * The bridge keeps an answer as one `text`: what Hermes said between its steps ("Auth and
 * Sunday's calendar first, then I'll put the reminder on…"), paragraph by paragraph, and
 * then the answer itself. Only `say`, the line before the first step, is kept apart.
 *
 * The chat draws the in-between lines with the steps, greyed, and only the answer as the
 * reply, the way Claude's apps show a turn. Nothing marks where the answer starts, so this
 * reads the opening paragraphs as the web's `splitAnswer` does, a little wider: a paragraph
 * is narration when it is short, plain prose, and one of its sentences says what Hermes is
 * about to do ("I'll pull its reminders", "Checking whether…"). The first paragraph that
 * isn't ends the narration, and the last paragraph is always the answer's.
 */
object HermesNarration {

    data class Split(val notes: List<String>, val answer: String)

    /**
     * [steps] bounds the narration: each in-between line came before at least one step, so a
     * turn with no steps has none. The web keeps only the last 20 steps, so 20 is no bound.
     */
    fun split(text: String, steps: Int): Split {
        val blocks = blocksOf(text)
        if (steps <= 0 || blocks.size < 2) return Split(emptyList(), text.trim())
        val limit = if (steps >= STEP_CAP) blocks.size - 1 else minOf(steps, blocks.size - 1)
        var n = 0
        while (n < limit && isNarration(blocks[n])) n++
        if (n == 0) return Split(emptyList(), text.trim())
        return Split(blocks.take(n), blocks.drop(n).joinToString("\n\n"))
    }

    /**
     * A turn the phone watched: the stream said which words came before a step, so those are
     * the notes. When the text doesn't start with them (the bridge tidied it), read it instead.
     */
    fun split(text: String, notes: List<String>?, steps: Int): Split {
        if (notes.isNullOrEmpty()) return split(text, steps)
        var rest = text.trimStart()
        for (note in notes) {
            val n = note.trim()
            if (!rest.startsWith(n)) return split(text, steps)
            rest = rest.removePrefix(n).trimStart()
        }
        return if (rest.isBlank()) split(text, steps) else Split(notes.map { it.trim() }, rest.trim())
    }

    /** Paragraphs, never cutting through a code fence. */
    fun blocksOf(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = ArrayList<String>()
        var fenced = false
        for (line in text.split('\n')) {
            if (line.trimStart().startsWith("```")) fenced = !fenced
            else if (!fenced && line.isBlank()) {
                if (cur.isNotEmpty()) out += cur.joinToString("\n").trim()
                cur.clear()
                continue
            }
            cur += line
        }
        if (cur.isNotEmpty()) out += cur.joinToString("\n").trim()
        return out.filter { it.isNotBlank() }
    }

    fun isNarration(block: String): Boolean {
        val b = block.trim()
        if (b.isEmpty() || b.length > MAX_NOTE || b.lines().size > 3) return false
        if (STRUCTURE.containsMatchIn(b)) return false
        return SENTENCE_BREAK.split(b).any { s ->
            val t = s.trim()
            INTENT.containsMatchIn(t) || IN_PROGRESS.containsMatchIn(t) || MOVING_ON.containsMatchIn(t) || isDoing(t)
        }
    }

    /** "Checking whether…", "Now merging every snapshot": a sentence that opens on what is being done. */
    private fun isDoing(sentence: String): Boolean {
        val word = DOING.find(sentence)?.groupValues?.get(1)?.lowercase() ?: return false
        return word !in NOT_DOING
    }

    private const val MAX_NOTE = 420
    private const val STEP_CAP = 20

    /** Headings, tables, lists, quotes and fences are an answer's shape, never a passing line. */
    private val STRUCTURE = Regex("""^(#|\||[-*+] |\d+[.)] |>|```)""", RegexOption.MULTILINE)

    private val SENTENCE_BREAK = Regex("""(?<=[.!?…])\s+""")

    /** "…then I'll put the reminder on", "Let me look", "I'm going to restart it". */
    private val INTENT = Regex(
        """\b(?:i['’]ll|i will|i['’]m going to|i am going to|let me|let['’]s)\s+[a-z]""",
        RegexOption.IGNORE_CASE,
    )

    /** "so I'm splitting the server into its own step", "Now I'm running the full pipeline": said mid-work, never in a summary of it. */
    private val IN_PROGRESS = Regex(
        """\bi(?:['’]m| am)\s+(?:now\s+|also\s+|still\s+|just\s+)?(?!(?:seeing|assuming|guessing|expecting|hoping|missing|having|being|feeling|going)\b)[a-z-]{3,}ing\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "Now the shifts table, the Buen.md block and the daily-note lines." */
    private val MOVING_ON = Regex("""^(?:now|next)[,:]?\s+(?:the|for|on to|onto|to)\b""", RegexOption.IGNORE_CASE)

    /**
     * A sentence opening on an -ing word, after "Now", "Next" and the like: "Checking if that's
     * a series", "Binary-searching for the last page". Any such word counts but the ones in
     * [NOT_DOING]; a list of verbs missed "Merging", "Working out" and whatever came next.
     */
    private val DOING = Regex(
        """^(?:(?:ok(?:ay)?|so|first|now|next|then|alright|right|also)[,:]?\s+)?([a-z][a-z-]{2,}ing)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Words in -ing that open a statement, not a step. */
    private val NOT_DOING = setOf(
        "nothing", "something", "everything", "anything", "during", "according", "regarding", "depending", "assuming",
        "being", "having", "using", "including", "excluding", "following", "pending", "missing", "existing", "remaining",
        "interesting", "surprising", "warning", "morning", "evening", "string", "thing", "bring", "spring", "considering",
    )
}
