package com.macrotracker.data.github

import java.time.LocalDate

/**
 * A year of contributions laid out as the graph draws it: one column per week,
 * Monday in row 0 and Sunday in row 6, the same way the t3lluz dashboard lays it out.
 *
 * Cells are indexed `week * 7 + row`. Padding before the first day has no date and
 * [levels] −1, so the snake can walk over it but there is nothing there to eat.
 */
class ContributionGrid private constructor(
    val weeks: Int,
    val levels: IntArray,
    val counts: IntArray,
    val dates: Array<String?>,
    /** Stable for a given year of data, so an unchanged year keeps its lap going. */
    val signature: String,
) {
    val cellCount: Int get() = weeks * 7

    companion object {
        fun from(contributions: GitHubContributions): ContributionGrid? {
            val days = contributions.days
            if (days.isEmpty()) return null
            val first = runCatching { LocalDate.parse(days.first().date) }.getOrNull() ?: return null
            // DayOfWeek.value is Monday = 1 … Sunday = 7, so Monday lands in row 0.
            val pad = first.dayOfWeek.value - 1
            val total = pad + days.size
            val weeks = (total + 6) / 7
            val levels = IntArray(weeks * 7) { -1 }
            val counts = IntArray(weeks * 7)
            val dates = arrayOfNulls<String>(weeks * 7)
            days.forEachIndexed { i, day ->
                val cell = pad + i
                levels[cell] = day.level.coerceIn(0, 4)
                counts[cell] = day.count
                dates[cell] = day.date
            }
            return ContributionGrid(
                weeks = weeks,
                levels = levels,
                counts = counts,
                dates = dates,
                signature = "${days.size}:${contributions.total}:${days.last().date}",
            )
        }
    }
}

/**
 * The snake's lap over one [ContributionGrid]: every cell it steps on, in order, and
 * the step on which each lit day is eaten.
 */
class SnakeRoute(
    /** Cells in walking order; consecutive entries are always grid neighbours. */
    val cells: IntArray,
    /** For each cell, the route step that eats it, or −1 if it is never lit. */
    val biteStep: IntArray,
)

/**
 * Platane/snk's route, as the t3lluz dashboard reads it back out of the profile SVG.
 *
 * The snake clears the lightest days first and finishes on the darkest, never
 * mixing layers. Inside a layer it goes to the nearest day it is due to eat, and it
 * walks *round* the busier days it is not due to eat yet rather than over them, so
 * whatever it stands on is always a day it just ate. That is a greedy nearest
 * neighbour over a Dijkstra that makes crossing a still-lit day expensive rather
 * than forbidden.
 *
 * Ported line for line from `snkSolve()` in the dashboard's github-snake.js,
 * including tie-breaks, so the phone and the web walk the same year the same way.
 */
object ContributionSnake {

    /** Head plus four body segments, as snk has it. */
    const val LENGTH = 5

    /** Comfortably past the longest detour worth taking on a grid seven rows deep. */
    private const val CROSS = 64

    fun solve(grid: ContributionGrid): SnakeRoute {
        val w = grid.weeks
        val n = w * 7
        val level = IntArray(n) { grid.levels[it].coerceAtLeast(0) }

        val route = ArrayList<Int>(n * 2)
        route += 3 // cell (0, 3): in at the left edge, halfway down
        val bite = IntArray(n) { -1 }
        // Insertion-ordered, like a JS Set, so ties break exactly as they do on the web.
        val lit = LinkedHashSet<Int>()
        for (c in 0 until n) if (level[c] > 0) lit += c

        val cost = IntArray(n)
        val from = IntArray(n)
        val seen = BooleanArray(n)
        val heapN = IntArray(n * 4 + 8)
        val heapC = IntArray(n * 4 + 8)
        var size = 0

        fun push(node: Int, c: Int) {
            var i = size++
            heapN[i] = node
            heapC[i] = c
            while (i > 0) {
                val p = (i - 1) shr 1
                if (heapC[p] <= heapC[i]) break
                val tn = heapN[p]
                val tc = heapC[p]
                heapN[p] = heapN[i]
                heapC[p] = heapC[i]
                heapN[i] = tn
                heapC[i] = tc
                i = p
            }
        }

        fun pop(): Int {
            val top = heapN[0]
            if (--size > 0) {
                heapN[0] = heapN[size]
                heapC[0] = heapC[size]
                var i = 0
                while (true) {
                    val l = i * 2 + 1
                    val r = l + 1
                    var m = i
                    if (l < size && heapC[l] < heapC[m]) m = l
                    if (r < size && heapC[r] < heapC[m]) m = r
                    if (m == i) break
                    val tn = heapN[m]
                    val tc = heapC[m]
                    heapN[m] = heapN[i]
                    heapC[m] = heapC[i]
                    heapN[i] = tn
                    heapC[i] = tc
                    i = m
                }
            }
            return top
        }

        // Arriving somewhere is free; the cost is charged on leaving, so the last step
        // onto a target is never priced against it.
        fun reach(block: Set<Int>) {
            cost.fill(-1)
            seen.fill(false)
            size = 0
            val start = route.last()
            cost[start] = 0
            from[start] = -1
            push(start, 0)
            while (size > 0) {
                val c = pop()
                if (seen[c]) continue
                seen[c] = true
                val cw = c / 7
                val cy = c % 7
                val d = cost[c] + 1 + if (c != start && c in lit) CROSS else 0
                for (k in 0 until 4) {
                    val nw = cw + if (k < 2) k * 2 - 1 else 0
                    val ny = cy + if (k < 2) 0 else k * 2 - 5
                    if (nw < 0 || nw >= w || ny < 0 || ny > 6) continue
                    val m = nw * 7 + ny
                    if (seen[m] || m in block) continue
                    if (cost[m] < 0 || d < cost[m]) {
                        cost[m] = d
                        from[m] = c
                        push(m, d)
                    }
                }
            }
        }

        // It starts on the left edge, which may itself be a day worth having.
        if (lit.remove(route[0])) bite[route[0]] = 0

        fun aim(lv: Int): Int {
            var best = -1
            var near = Int.MAX_VALUE
            for (c in lit) {
                if (level[c] == lv && cost[c] >= 0 && cost[c] < near) {
                    near = cost[c]
                    best = c
                }
            }
            return best
        }

        fun owed(lv: Int): Boolean = lit.any { level[it] == lv }

        val body = HashSet<Int>()
        for (lv in 1..4) {
            var guard = n
            while (owed(lv) && guard-- > 0) {
                body.clear()
                var i = 1
                while (i < LENGTH && i < route.size) {
                    body += route[route.size - 1 - i]
                    i++
                }
                reach(body)
                var best = aim(lv)
                // Only a spiral into a corner can wall the head in with its own tail.
                // Letting it plan through itself costs a lap's tidiness and saves the lap.
                if (best < 0) {
                    body.clear()
                    reach(body)
                    best = aim(lv)
                }
                if (best < 0) break

                val at = route.last()
                val leg = ArrayList<Int>()
                var c = best
                while (c != at) {
                    leg += c
                    c = from[c]
                }
                leg.reverse()
                for (cell in leg) {
                    route += cell
                    // Whatever it stands on is eaten there and then, so the head is
                    // never sitting on a day that is still lit.
                    if (lit.remove(cell)) bite[cell] = route.size - 1
                }
            }
        }
        return SnakeRoute(route.toIntArray(), bite)
    }
}
