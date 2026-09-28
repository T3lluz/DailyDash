package com.macrotracker.ui.components

/**
 * How much of an item the island's line shows, richest first. The line never scrolls:
 * when its items don't fit, they give up words in this order until they do, and the icon
 * always stays.
 */
enum class IslandFold {
    /** Title, detail, progress ring and countdown (the first item only). */
    DETAIL,

    /** Title, ring and countdown. */
    TITLE,

    /** A word or two for the title (the AI's short title), ring and countdown. */
    SHORT,

    /** The short title and ring, no countdown. */
    SHORT_BARE,

    /** The icon alone. */
    ICON,
}

/** An item's width in each [IslandFold], in any one unit. */
data class IslandFoldWidths(
    val detail: Float,
    val title: Float,
    val short: Float,
    val shortBare: Float,
    val icon: Float,
) {
    operator fun get(fold: IslandFold): Float = when (fold) {
        IslandFold.DETAIL -> detail
        IslandFold.TITLE -> title
        IslandFold.SHORT -> short
        IslandFold.SHORT_BARE -> shortBare
        IslandFold.ICON -> icon
    }
}

/**
 * What the line shows: a fold per item, and how many items it shows. Items past [shown]
 * are counted in a "+N" at the end, only when even their icons can't all fit.
 */
data class IslandFit(val folds: List<IslandFold>, val shown: Int) {
    val hidden: Int get() = folds.size - shown
}

/**
 * Fits [widths] (one per item, most pressing first) into [available], with [gap] between
 * items. The first item starts with its detail and the rest with their titles. Each step
 * then takes one fold from every item in turn, the last item first, so the most pressing
 * item keeps its words longest: first the detail goes, then titles shorten, then
 * countdowns go, then items fold to their icons. When even all icons overflow, trailing
 * items are dropped behind a "+N" of [overflowWidth].
 */
fun fitIslandLine(
    widths: List<IslandFoldWidths>,
    available: Float,
    gap: Float,
    overflowWidth: (hidden: Int) -> Float,
): IslandFit {
    val n = widths.size
    if (n == 0) return IslandFit(emptyList(), 0)
    val folds = MutableList(n) { if (it == 0) IslandFold.DETAIL else IslandFold.TITLE }
    fun total(count: Int = n): Float =
        (0 until count).sumOf { widths[it][folds[it]].toDouble() }.toFloat() + gap * (count - 1)

    if (total() <= available) return IslandFit(folds.toList(), n)
    for (level in IslandFold.entries.drop(1)) {
        for (i in n - 1 downTo 0) {
            if (folds[i] >= level) continue
            folds[i] = level
            if (total() <= available) return IslandFit(folds.toList(), n)
        }
    }
    for (shown in n - 1 downTo 1) {
        if (total(shown) + gap + overflowWidth(n - shown) <= available) return IslandFit(folds.toList(), shown)
    }
    return IslandFit(folds.toList(), 1)
}
