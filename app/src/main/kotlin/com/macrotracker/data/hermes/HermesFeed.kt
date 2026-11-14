package com.macrotracker.data.hermes

/**
 * The transcript as it is shown, after T3 Code's phone app: a finished run (everything
 * between what you said and Hermes' last answer to it) folds into one line, "Worked for 1m
 * 12s · 9 steps", with the answer under it. Tapped, the fold opens to every step as it
 * was. The run that is still going is never folded, and nothing that waits on you (an
 * approval, a question) ever is.
 */
sealed interface FeedRow {
    val key: String

    /** A transcript item, shown as it is. */
    data class Item(val item: HermesItem) : FeedRow {
        override val key: String get() = item.key
    }

    /** The work behind an answer, folded: the steps in order, how long they took together. */
    data class Run(override val key: String, val items: List<HermesItem>, val ms: Long?, val steps: Int) : FeedRow
}

object HermesFeed {

    /** A run folds only when there is this much to fold: one stray step reads better in place. */
    const val MIN_FOLDED = 2

    /** [items] as rows. [working] is whether a turn is running now: the run it belongs to stays open. */
    fun rows(items: List<HermesItem>, working: Boolean): List<FeedRow> {
        val out = ArrayList<FeedRow>(items.size)
        var start = 0
        while (start < items.size) {
            // one run: up to, not including, the next thing you said
            var end = start + 1
            while (end < items.size && items[end] !is HermesItem.User) end++
            val last = end == items.size
            fold(items.subList(start, end), keepOpen = last && working, out)
            start = end
        }
        return out
    }

    private fun fold(run: List<HermesItem>, keepOpen: Boolean, out: MutableList<FeedRow>) {
        val answer = run.indexOfLast { it is HermesItem.Assistant && it.text.isNotBlank() }
        val work = if (keepOpen || answer < 0) emptyList() else run.subList(0, answer).filter(::foldable)
        if (work.size < MIN_FOLDED) {
            run.forEach { out += FeedRow.Item(it) }
            return
        }
        val folded = work.toSet()
        var placed = false
        for ((i, item) in run.withIndex()) {
            if (item in folded) {
                if (!placed) {
                    placed = true
                    out += FeedRow.Run(
                        key = "run-${work.first().key}",
                        items = work,
                        ms = time(work + run[answer]),
                        steps = work.sumOf(::steps),
                    )
                }
            } else {
                out += FeedRow.Item(item)
            }
            if (i == answer) placed = true
        }
    }

    /** Work, as against what you said, what waits on you, and what went wrong. */
    private fun foldable(item: HermesItem): Boolean = when (item) {
        is HermesItem.Assistant, is HermesItem.Web, is HermesItem.Exec, is HermesItem.Output -> true
        is HermesItem.User, is HermesItem.Ask, is HermesItem.Clarify, is HermesItem.Error -> false
    }

    private fun steps(item: HermesItem): Int = when (item) {
        is HermesItem.Assistant -> item.tools.size
        is HermesItem.Web, is HermesItem.Exec -> 1
        else -> 0
    }

    /** The run's turns added up; null when none of them was timed. */
    private fun time(items: List<HermesItem>): Long? {
        val timed = items.mapNotNull { (it as? HermesItem.Assistant)?.ms ?: (it as? HermesItem.Exec)?.ms }
        return timed.takeIf { it.isNotEmpty() }?.sum()
    }
}
