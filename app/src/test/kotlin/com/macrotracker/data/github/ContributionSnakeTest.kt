package com.macrotracker.data.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The snake is a port of `snkSolve()` from the t3lluz dashboard's github-snake.js, and
 * the phone should walk a year exactly the way the web does. The expected figures below
 * come from running the JavaScript itself (Node) on the same generated grids.
 */
class ContributionSnakeTest {

    private data class Case(val pad: Int, val days: Int, val seed: Long, val len: Int, val hash: Int, val bites: Int, val bhash: Int)

    private val cases = listOf(
        Case(pad = 3, days = 365, seed = 42, len = 715, hash = 556593986, bites = 174, bhash = -1513346030),
        Case(pad = 0, days = 371, seed = 7, len = 733, hash = -1005791213, bites = 197, bhash = 1047817721),
        Case(pad = 5, days = 79, seed = 99, len = 132, hash = 177869952, bites = 36, bhash = 1127482136),
    )

    @Test
    fun `walks the same route as the web`() {
        cases.forEach { c ->
            val grid = gridFor(c.pad, c.days, c.seed)
            val route = ContributionSnake.solve(grid)
            assertEquals("route length for seed ${c.seed}", c.len, route.cells.size)
            var h = 0
            route.cells.forEach { h = h * 31 + it }
            assertEquals("route hash for seed ${c.seed}", c.hash, h)
            val bitten = route.biteStep.withIndex().filter { it.value >= 0 }
            assertEquals("bites for seed ${c.seed}", c.bites, bitten.size)
            var b = 0
            bitten.forEach { (cell, step) -> b = b * 31 + cell * 7 + step }
            assertEquals("bite hash for seed ${c.seed}", c.bhash, b)
        }
    }

    @Test
    fun `eats every lit day and only ever steps to a neighbouring cell`() {
        val grid = gridFor(pad = 3, days = 365, seed = 42)
        val route = ContributionSnake.solve(grid)
        val lit = grid.levels.withIndex().filter { it.value > 0 }.map { it.index }
        lit.forEach { assertTrue("day $it is eaten", route.biteStep[it] >= 0) }

        // Consecutive cells are grid neighbours.
        for (i in 1 until route.cells.size) {
            val a = route.cells[i - 1]
            val b = route.cells[i]
            val step = kotlin.math.abs(a / 7 - b / 7) + kotlin.math.abs(a % 7 - b % 7)
            assertEquals("step $i is one cell", 1, step)
        }
        // The darkest layer is the one it finishes on.
        val last = lit.maxBy { route.biteStep[it] }
        assertEquals(4, grid.levels[last])
    }

    @Test
    fun `lays out weeks from Monday`() {
        val monday = LocalDate.of(2025, 9, 22)
        val contributions = GitHubContributions(
            total = 3,
            days = (0 until 10).map { GitHubContributionDay(monday.plusDays(it.toLong()).toString(), if (it == 2) 3 else 0, if (it == 2) 4 else 0) },
        )
        val grid = ContributionGrid.from(contributions)!!
        assertEquals(2, grid.weeks)
        assertEquals(4, grid.levels[2]) // Wednesday, row 2 of week 0
        assertEquals(-1, grid.levels[13]) // past the last day
    }

    @Test
    fun `counts streaks the way the profile does`() {
        val start = LocalDate.of(2026, 9, 1)
        val counts = listOf(1, 1, 0, 2, 3, 1, 0, 0, 4, 5, 0) // today (last) is still empty
        val c = GitHubContributions(
            total = counts.sum(),
            days = counts.mapIndexed { i, n -> GitHubContributionDay(start.plusDays(i.toLong()).toString(), n, if (n > 0) 1 else 0) },
        )
        assertEquals(2, c.currentStreak)
        assertEquals(3, c.longestStreak)
    }

    /** The same grid the Node run built: an LCG over each day's level, padded to a Monday start. */
    private fun gridFor(pad: Int, days: Int, seed: Long): ContributionGrid {
        var s = seed and 0xFFFFFFFFL
        val first = LocalDate.of(2025, 1, 6).with(DayOfWeek.MONDAY).plusDays(pad.toLong())
        val list = (0 until days).map { i ->
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val v = (s % 10).toInt()
            val level = when {
                v < 5 -> 0
                v < 7 -> 1
                v < 8 -> 2
                v < 9 -> 3
                else -> 4
            }
            GitHubContributionDay(first.plusDays(i.toLong()).toString(), level, level)
        }
        return ContributionGrid.from(GitHubContributions(total = list.sumOf { it.count }, days = list))!!
    }
}
