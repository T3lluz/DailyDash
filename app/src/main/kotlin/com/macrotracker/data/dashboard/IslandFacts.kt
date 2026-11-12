package com.macrotracker.data.dashboard

/**
 * A number the island shows on its right when one item has it to itself: a value over the
 * word that says what it is ("35m" over "left", "48%" over "CPU"), after Android 17's
 * metric-style Live Updates and the trailing side of iOS's Dynamic Island.
 */
data class IslandFact(val value: String, val label: String)

/**
 * One item alone on the island, split the way the Dynamic Island splits one activity: what
 * it is on the left ([detail] under the title), its live numbers on the right ([facts], at
 * most [MAX_FACTS]), or the one thing to do about it ([action], "Answer", "Review").
 */
data class IslandSolo(
    val facts: List<IslandFact>,
    val detail: String,
    val action: String,
    /** The first fact is the item's countdown (drawn in its tone), not a number from its detail. */
    val leadIsEnd: Boolean = false,
)

/** The most numbers the island puts side by side on its right. */
const val MAX_FACTS = 3

/**
 * Splits [item] into the island's two sides. The countdown comes first ("35 min left" ->
 * 35m / left); then each part of the detail line (" · " between them) that is a number
 * becomes a fact ("CPU 48%", "until 18:00", "7° / 4°", "14:30–15:30"), and the parts that
 * are words ("Living room TV", "Room 4") stay together as the detail. A number the title
 * already says ("Server 73°" and "73°") is not said twice. Pure, so IslandFactsTest pins it.
 */
fun islandSolo(item: IslandItem): IslandSolo {
    val title = item.title.trim()
    val facts = mutableListOf<IslandFact>()
    var action = ""
    val words = mutableListOf<String>()
    val end = item.end.trim()
    val endFact = endFact(item)
    when {
        endFact != null -> facts += endFact
        // The server says "now" in an event's last half-minute ("now left", "in now"): nothing to show.
        NOW.matches(end) -> Unit
        end.lowercase() in ACTIONS -> action = end
        end.isNotEmpty() && end.none(Char::isDigit) -> words += end
    }
    item.sub.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
        val found = subFacts(part, item.kind)
        if (found.isEmpty()) words += part else facts += found
    }
    val kept = facts
        .filterNot { it.value.isNotEmpty() && saysNumber(title, it.value) }
        .distinctBy { it.value to it.label }
        .take(MAX_FACTS)
    return IslandSolo(kept, words.joinToString(" · "), action, leadIsEnd = endFact != null && kept.firstOrNull() == endFact)
}

/** Whether [title] already says [value] as a number of its own: "Server 73°" says 73°, "F1" doesn't say 1. */
internal fun saysNumber(title: String, value: String): Boolean =
    Regex("""(?<![\w.,:])""" + Regex.escape(value) + """(?!\d|[.,]\d)""").containsMatchIn(title)

/** The countdown or count at the item's end as a fact, or null when it is a verb or nothing. */
internal fun endFact(item: IslandItem): IslandFact? {
    val end = item.end.trim()
    // A duration the countdown rules don't read ("2d 3h", "2d 3h left") reads as one value.
    if (DURATION.matches(end)) return subFacts(end, item.kind).singleOrNull()
    val value = compactIslandEnd(end)
    if (value.isEmpty()) return null
    val e = end.lowercase()
    val label = when {
        e.endsWith(" left") -> "left"
        e.startsWith("in ") -> "to go"
        CLOCK.matches(e) -> if (item.kind == "cal") "starts" else "at"
        item.kind == "live" -> "watching"
        else -> COUNT_WORD.find(e)?.groupValues?.get(1).orEmpty()
    }
    return IslandFact(value, label)
}

/** Ends that are something to do, shown as a button rather than a number. */
private val ACTIONS = setOf("answer", "review", "log", "update", "reply", "read", "open", "watch", "join")

private val NOW = Regex("""^(?:in\s+)?now(?:\s+left)?$""", RegexOption.IGNORE_CASE)
private val COUNT_WORD = Regex("""^\d+\s+([a-z]+)$""")
/** "1h 05m", "2d 3h", "25 min", each with an optional " left": one value, never words. */
private val DURATION = Regex(
    """^(?:(\d+)\s*d\s*(\d+)\s*h|(\d+)\s*h(?:\s*(\d+)\s*m(?:in)?)?|(\d+)\s*m(?:in)?)(\s+left)?$""",
    RegexOption.IGNORE_CASE,
)
private val CLOCK = Regex("""^\d{1,2}[:.]\d{2}$""")
private val HIGH_LOW = Regex("""^(-?\d+°)\s*/\s*(-?\d+°)$""")
private val RANGE = Regex("""^(\d{1,2}[:.]\d{2})\s*[–-]\s*(\d{1,2}[:.]\d{2})$""")
private val WORD_CLOCK = Regex("""^([A-Za-z]+)\s+(\d{1,2}[:.]\d{2})$""")
private val EPISODE = Regex("""^S\d+\s?E\d+$""", RegexOption.IGNORE_CASE)

/** A number with its unit when the unit reads as part of it ("48%", "7°", "45g", "3.2km", "1.2k"). */
private val NUMBER = Regex("""-?\d[\d.,:/]*(?:\s?(?:%|°|g|kg|km|k)(?![A-Za-z]))?""")

/** Short words that read as cut off at the end of a label ("protein to"). */
private val TRAILING = setOf("to", "of", "the", "a", "in", "on", "at", "for", "and", "go")

/** What a time is, named the way a metric's label names its value: "until 18:00" is when it ends. */
private fun clockLabel(word: String): String = when (word.lowercase()) {
    "until", "till", "to" -> "ends"
    "from", "at", "starts" -> "starts"
    else -> word.lowercase()
}

/** The facts in one part of an item's detail line, or none when the part is words. */
internal fun subFacts(part: String, kind: String): List<IslandFact> {
    val p = part.trim().trimEnd('.', ',', ':', '/')
    if (p.none(Char::isDigit) || p.length > 20) return emptyList()
    DURATION.find(p)?.let { m ->
        val g = m.groupValues
        val value = when {
            g[1].isNotEmpty() -> g[1] + "d" + g[2] + "h"
            g[3].isNotEmpty() -> g[3] + "h" + g[4].takeIf { it.isNotEmpty() }?.padStart(2, '0').orEmpty()
            else -> g[5] + "m"
        }
        return listOf(IslandFact(value, if (g[6].isNotEmpty()) "left" else ""))
    }
    HIGH_LOW.find(p)?.let { return listOf(IslandFact(it.groupValues[1], "high"), IslandFact(it.groupValues[2], "low")) }
    RANGE.find(p)?.let { return listOf(IslandFact(it.groupValues[1], "start"), IslandFact(it.groupValues[2], "end")) }
    WORD_CLOCK.find(p)?.let { return listOf(IslandFact(it.groupValues[2], clockLabel(it.groupValues[1]))) }
    if (CLOCK.matches(p)) return listOf(IslandFact(p, "at"))
    if (EPISODE.matches(p)) return listOf(IslandFact(p.uppercase().replace(" ", ""), "episode"))
    val number = NUMBER.find(p) ?: return emptyList()
    // A number inside words is a name ("Room 4", "Gate 12") unless it carries a unit ("CPU 48%").
    if (number.range.first > 0 && number.value.last().isDigit()) return emptyList()
    val value = number.value.replace(" ", "").trimEnd('.', ',', ':', '/')
    // A number glued to a word it doesn't own ("3x", "2nd") is a name, not a quantity.
    if (number.range.last + 1 < p.length && p[number.range.last + 1].isLetter()) return emptyList()
    val rest = (p.substring(0, number.range.first) + " " + p.substring(number.range.last + 1).substringBefore(','))
        .split(' ').filter { it.isNotBlank() && it.none(Char::isDigit) }
        .map { it.trim(',', '.', ';') }
    // The last word of a long rest is mostly filler ("protein to go"): keep the first one or two.
    var label = rest.take(2).dropLastWhile { it.lowercase() in TRAILING }.joinToString(" ")
    if (label.length > 12) label = rest.first()
    if (kind == "steps" && label.isEmpty()) label = "steps"
    return listOf(IslandFact(value, label))
}

/**
 * A fact as a cell's second line says it: a time after its word ("at 21:00", "starts 14:30"),
 * anything else before it ("35m left"); a value that names itself ("S03E06") alone.
 */
fun islandFactPhrase(fact: IslandFact): String = when {
    fact.label.isEmpty() || fact.label == "episode" -> fact.value
    fact.label in CLOCK_WORDS -> "${fact.label} ${fact.value}"
    else -> "${fact.value} ${fact.label}"
}

private val CLOCK_WORDS = setOf("at", "starts", "ends")

/** What kind of thing an item is, in a word, for when it has nothing else to say. */
fun islandKindName(kind: String): String = when {
    kind.startsWith("play") -> "Playing"
    else -> when (kind) {
        "cal" -> "Calendar"
        "mail" -> "Mail"
        "need" -> "Hermes"
        "wx", "rain", "now" -> "Weather"
        "f1", "race" -> "Formula 1"
        "brief" -> "Briefing"
        "srv", "host" -> "Server"
        "live" -> "Twitch"
        "yt" -> "YouTube"
        "gh" -> "GitHub"
        "show" -> "Coming up"
        "sleep", "steps", "food" -> "Health"
        "update" -> "Update"
        else -> ""
    }
}
