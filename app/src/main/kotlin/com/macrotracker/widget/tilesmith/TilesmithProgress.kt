package com.macrotracker.widget.tilesmith

/**
 * Reads a Tilesmith answer while it is still streaming, for the studio's live line: what the
 * AI is writing now ("Adding a ring · Steps"), and the heading of what it is thinking about.
 * Pure, so TilesmithProgressTest can pin it.
 */
object TilesmithProgress {
    private val TYPE = Regex(""""type"\s*:\s*"([a-z_]+)"""")
    private val NAME = Regex(""""name"\s*:\s*"([^"]{1,40})"""")
    private val LABEL = Regex(""""label"\s*:\s*"([^"]{1,30})"""")
    private val BIND = Regex(""""bind"\s*:\s*"([a-z0-9._]+)"""")
    private val HEADING = Regex("""\*\*([^*\n]{3,80})\*\*""")

    /**
     * What a partial answer says about the AI's progress, or null before there is anything to
     * say. The design's name, then each block as it starts, in the person's words: its label,
     * else its source's name.
     */
    fun describe(partial: CharSequence): String? {
        val text = partial.toString()
        val at = text.indexOf("\"design\"")
        if (at < 0) return if (text.contains("\"reply\"")) "Writing what it made" else null
        val design = text.substring(at)
        val types = TYPE.findAll(design).toList()
        if (types.isEmpty()) {
            return NAME.find(design)?.let { "Designing “${it.groupValues[1]}”" } ?: "Laying it out"
        }
        val lastType = types.last()
        val type = BlockType.byId(lastType.groupValues[1])
        val start = design.lastIndexOf('{', lastType.range.first).coerceAtLeast(0)
        val block = design.substring(start)
        val what = LABEL.find(block)?.groupValues?.get(1)
            ?: BIND.find(block)?.groupValues?.get(1)?.let { TileSources.byKey(it)?.label }
        // "Ring" reads "a ring"; an acronym ("AI line") keeps its capitals.
        val noun = type?.label?.let { l -> if (l.length > 1 && l[1].isUpperCase()) l else l.lowercase() } ?: "block"
        val article = if (noun.first().lowercaseChar() in "aeiou") "an" else "a"
        return buildString {
            append("Adding ").append(article).append(' ').append(noun)
            if (!what.isNullOrBlank() && !what.equals(noun, ignoreCase = true)) append(" · ").append(what)
        }
    }

    /**
     * The newest heading in the model's thinking summary ("**Planning the layout**"), else its
     * last sentence, cut to a line. Null while there is nothing worth showing.
     */
    fun thinkingLine(thinking: CharSequence): String? {
        val text = thinking.toString()
        HEADING.findAll(text).lastOrNull()?.let { return it.groupValues[1].trim() }
        val sentence = text.replace('\n', ' ').split(Regex("""(?<=[.!?])\s+""")).map { it.trim() }
            .lastOrNull { it.length >= 12 } ?: return null
        return if (sentence.length <= 64) sentence else sentence.take(63).trimEnd() + "…"
    }
}
