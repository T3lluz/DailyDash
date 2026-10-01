package com.macrotracker.ui.components

/**
 * How much of an item the island's line shows, richest first. The line holds at most three
 * items and never scrolls: when they don't fit, they give up words in this order until they
 * do, and the icon always stays.
 */
enum class IslandFold {
    /** Title, detail, progress ring and countdown (the first item only). */
    DETAIL,

    /** Title, ring and countdown. */
    TITLE,

    /** The label (a word or two the AI wrote to fit three across), ring and countdown. */
    LABEL,

    /** The label and ring, no countdown. */
    LABEL_BARE,
}

/**
 * An item's width in each [IslandFold], in any one unit. [labelText] is the label's own
 * text inside [labelBare]; the rest of it ([labelBare] − [labelText]) is the icon and padding.
 */
data class IslandFoldWidths(
    val detail: Float,
    val title: Float,
    val label: Float,
    val labelBare: Float,
    val labelText: Float,
) {
    operator fun get(fold: IslandFold): Float = when (fold) {
        IslandFold.DETAIL -> detail
        IslandFold.TITLE -> title
        IslandFold.LABEL -> label
        IslandFold.LABEL_BARE -> labelBare
    }

    val chrome: Float get() = labelBare - labelText
}

/**
 * What the line shows: a fold for each of the first [shown] items, and how many more wait
 * behind a "+N". [labelCap], when set, is the most any label may take; longer ones are cut
 * short so three still fit on a narrow screen or with large text.
 */
data class IslandFit(val folds: List<IslandFold>, val shown: Int, val total: Int, val labelCap: Float? = null) {
    val hidden: Int get() = total - shown
}

/**
 * Fits the first [maxShown] of [widths] (one per item, most pressing first) into
 * [available], with [gap] between items and, when items are left over, a "+N" of
 * [overflowWidth]. The first item starts with its detail and the rest with their titles.
 * Each step then takes one fold from every item in turn, the last item first, so the most
 * pressing item keeps its words longest: first the detail goes, then titles turn into
 * labels, then countdowns go. Labels that still don't fit share the room that is left, each
 * cut to the same cap; only when that cap falls under [minLabel] does the line show one
 * item fewer.
 */
fun fitIslandLine(
    widths: List<IslandFoldWidths>,
    available: Float,
    gap: Float,
    minLabel: Float,
    maxShown: Int = 3,
    overflowWidth: (hidden: Int) -> Float,
): IslandFit {
    val n = widths.size
    if (n == 0) return IslandFit(emptyList(), 0, 0)
    for (shown in minOf(n, maxShown) downTo 1) {
        val hidden = n - shown
        val room = available - if (hidden > 0) gap + overflowWidth(hidden) else 0f
        val folds = MutableList(shown) { if (it == 0) IslandFold.DETAIL else IslandFold.TITLE }
        fun total(): Float = (0 until shown).sumOf { widths[it][folds[it]].toDouble() }.toFloat() + gap * (shown - 1)

        if (total() <= room) return IslandFit(folds.toList(), shown, n)
        for (level in IslandFold.entries.drop(1)) {
            for (i in shown - 1 downTo 0) {
                if (folds[i] >= level) continue
                folds[i] = level
                if (total() <= room) return IslandFit(folds.toList(), shown, n)
            }
        }
        // Every item is down to its label: cut the longest labels to a shared cap.
        val labels = (0 until shown).map { widths[it].labelText }
        val forLabels = room - (0 until shown).sumOf { widths[it].chrome.toDouble() }.toFloat() - gap * (shown - 1)
        val cap = shareCap(labels, forLabels)
        if (cap >= minLabel || shown == 1) return IslandFit(folds.toList(), shown, n, cap.coerceAtLeast(0f))
    }
    return IslandFit(listOf(IslandFold.LABEL_BARE), 1, n, 0f)
}

/**
 * The largest cap such that the [lengths], each cut to it, sum to at most [room]: short
 * labels keep their length, long ones share what is left equally.
 */
internal fun shareCap(lengths: List<Float>, room: Float): Float {
    if (lengths.isEmpty()) return room
    val sorted = lengths.sorted()
    var left = room
    for ((i, len) in sorted.withIndex()) {
        val share = left / (sorted.size - i)
        if (len > share) return share
        left -= len
    }
    return sorted.last()
}
