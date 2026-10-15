package com.macrotracker.widget.tilesmith

import androidx.compose.ui.unit.DpSize
import com.macrotracker.widget.kit.WidgetDims

/**
 * Hand-made designs: the studio's one-tap starts, the draft shown while the AI works, the
 * picker's previews, and the AI's examples of what good looks like.
 */
object TileStarters {
    data class Starter(val id: String, val title: String, val prompt: String, val keywords: List<String>, val design: TileDesign)

    private fun d(name: String, size: TileSize, palette: TilePalette, surface: TileSurface, refresh: Int, vararg rows: TileRow) =
        TileDesign(id = "starter_" + name.lowercase().replace(' ', '_'), name = name, size = size, palette = palette, surface = surface, refreshMinutes = refresh, rows = rows.toList(), createdAt = 0L, updatedAt = 0L)

    private fun row(h: Int, vararg cells: TileBlock) = TileRow(h, cells.toList())

    val all: List<Starter> = listOf(
        Starter(
            "today", "Today", "My day: the time, the weather and what's next in my calendar",
            listOf("day", "morning", "today", "glance", "clock", "time", "weather", "calendar", "next"),
            d(
                "Today", TileSize.WIDE, TilePalette.WALLPAPER, TileSurface.TONAL, 5,
                row(
                    1,
                    TileBlock(BlockType.CLOCK, weight = 2, bind = "time.clock", style = BlockStyle.PLAIN),
                    TileBlock(BlockType.BADGE, bind = "weather.now", shape = TileShape.SUNNY, style = BlockStyle.SHAPE, tone = TileTone.TERTIARY),
                ),
                row(
                    1,
                    TileBlock(BlockType.COUNTDOWN, weight = 2, bind = "calendar.next", tone = TileTone.SECONDARY),
                    TileBlock(BlockType.METRIC, bind = "weather.rain", label = "Rain", tone = TileTone.PRIMARY),
                ),
            ),
        ),
        Starter(
            "move", "Move rings", "Steps and active calories as rings, with last night's sleep",
            listOf("steps", "rings", "activity", "move", "fitness", "health", "calories", "sleep"),
            d(
                "Move", TileSize.SMALL, TilePalette.WALLPAPER, TileSurface.TONAL, 15,
                row(2, TileBlock(BlockType.RING, weight = 1, bind = "health.steps", label = "Steps")),
                row(
                    1,
                    TileBlock(BlockType.METRIC, bind = "health.active_cal", label = "Move", tone = TileTone.TERTIARY),
                    TileBlock(BlockType.METRIC, bind = "health.sleep", label = "Sleep", tone = TileTone.SECONDARY),
                ),
            ),
        ),
        Starter(
            "server", "Server pulse", "My server: health, CPU and memory, and CPU over the last day",
            listOf("server", "cpu", "memory", "ram", "disk", "docker", "homelab", "ssh", "t3lluz"),
            d(
                "Server pulse", TileSize.WIDE, TilePalette.MINT, TileSurface.TONAL, 5,
                row(
                    1,
                    TileBlock(BlockType.METRIC, weight = 2, bind = "server.status", style = BlockStyle.FILLED),
                    TileBlock(BlockType.RING, bind = "server.cpu", label = "CPU"),
                    TileBlock(BlockType.RING, bind = "server.mem", label = "Memory", tone = TileTone.TERTIARY),
                ),
                row(1, TileBlock(BlockType.CHART, bind = "server.cpu_history", label = "CPU, 24 h", variant = "line")),
            ),
        ),
        Starter(
            "f1", "Race week", "Countdown to the next F1 session and the championship top three",
            listOf("f1", "formula", "race", "grand prix", "gp", "qualifying", "standings", "norris", "verstappen"),
            d(
                "Race week", TileSize.WIDE, TilePalette.RACING, TileSurface.TONAL, 15,
                row(
                    1,
                    TileBlock(BlockType.BADGE, bind = "f1.next", icon = "flag", shape = TileShape.SOFT_BURST, style = BlockStyle.SHAPE),
                    TileBlock(BlockType.COUNTDOWN, weight = 2, bind = "f1.next", style = BlockStyle.PLAIN),
                ),
                row(1, TileBlock(BlockType.LIST, bind = "f1.drivers", max = 3)),
            ),
        ),
        Starter(
            "battery", "Battery", "Battery as a ring and how full my storage is",
            listOf("battery", "charge", "charging", "storage", "phone", "power"),
            d(
                "Battery", TileSize.MINI, TilePalette.WALLPAPER, TileSurface.TONAL, 2,
                row(
                    1,
                    TileBlock(BlockType.RING, bind = "phone.battery", style = BlockStyle.PLAIN),
                    TileBlock(BlockType.METRIC, bind = "phone.storage", label = "Storage", tone = TileTone.SECONDARY),
                ),
            ),
        ),
        Starter(
            "rain", "Rain check", "Will it rain today, the temperature now and the next hours",
            listOf("rain", "umbrella", "weather", "forecast", "temperature", "wind"),
            d(
                "Rain check", TileSize.BAR, TilePalette.OCEAN, TileSurface.TONAL, 15,
                row(
                    1,
                    TileBlock(BlockType.BADGE, bind = "weather.now", shape = TileShape.COOKIE_12, style = BlockStyle.SHAPE),
                    TileBlock(BlockType.METRIC, weight = 2, bind = "weather.rain", style = BlockStyle.PLAIN),
                    TileBlock(BlockType.CHART, weight = 2, bind = "weather.hourly", style = BlockStyle.PLAIN, variant = "line"),
                ),
            ),
        ),
        Starter(
            "agenda", "Up next", "The rest of today's calendar",
            listOf("agenda", "schedule", "meetings", "events", "calendar list", "today's events"),
            d(
                "Up next", TileSize.WIDE, TilePalette.WALLPAPER, TileSurface.SOFT, 5,
                row(
                    1,
                    TileBlock(BlockType.DATE, bind = "time.date", style = BlockStyle.PLAIN),
                    TileBlock(BlockType.METRIC, bind = "calendar.count", label = "Left today", style = BlockStyle.PLAIN, tone = TileTone.SECONDARY),
                ),
                row(2, TileBlock(BlockType.LIST, bind = "calendar.today")),
            ),
        ),
        Starter(
            "headline", "Tech headline", "Today's most important tech headline, searched on the web",
            listOf("news", "headline", "tech", "web", "search", "internet", "stocks", "price", "bitcoin"),
            d(
                "Tech headline", TileSize.WIDE, TilePalette.LAVENDER, TileSurface.TINTED, 60,
                row(
                    1,
                    TileBlock(BlockType.BADGE, icon = "globe", shape = TileShape.FLOWER, style = BlockStyle.SHAPE),
                    TileBlock(BlockType.AI, weight = 3, bind = "ai.text", text = "The single most important tech headline today, in one sentence.", web = true, every = 120, style = BlockStyle.PLAIN),
                ),
            ),
        ),
        Starter(
            "fuel", "Fuel", "Calories and protein against my goals",
            listOf("food", "calories", "protein", "macros", "nutrition", "diet", "eat"),
            d(
                "Fuel", TileSize.SMALL, TilePalette.SUNSET, TileSurface.TONAL, 5,
                row(2, TileBlock(BlockType.RING, bind = "food.calories", label = "Calories")),
                row(1, TileBlock(BlockType.BAR, bind = "food.protein", label = "Protein", tone = TileTone.TERTIARY)),
            ),
        ),
        Starter(
            "github", "Code review", "GitHub reviews waiting on me and my open pull requests",
            listOf("github", "pull request", "pr", "review", "code", "commits", "issues"),
            d(
                "Code review", TileSize.SMALL, TilePalette.MONO, TileSurface.TONAL, 10,
                row(
                    1,
                    TileBlock(BlockType.BADGE, bind = "github.reviews", shape = TileShape.COOKIE_9, style = BlockStyle.SHAPE, tone = TileTone.TERTIARY, label = "Reviews"),
                    TileBlock(BlockType.METRIC, bind = "github.prs", label = "My PRs"),
                ),
                row(1, TileBlock(BlockType.CHART, bind = "github.contrib", label = "Streak")),
            ),
        ),
        Starter(
            "claude", "Claude window", "How much of my Claude session and week is used",
            listOf("claude", "usage", "tokens", "limit", "hermes", "ai usage", "anthropic"),
            d(
                "Claude", TileSize.MINI, TilePalette.SAND, TileSurface.TONAL, 5,
                row(
                    1,
                    TileBlock(BlockType.RING, bind = "dash.claude", label = "Session", style = BlockStyle.PLAIN),
                    TileBlock(BlockType.RING, bind = "dash.claude_week", label = "Week", tone = TileTone.TERTIARY, style = BlockStyle.PLAIN),
                ),
            ),
        ),
        Starter(
            "dashboard", "Everything", "A big dashboard: time, weather, steps, my next event, server health and an AI line",
            listOf("everything", "dashboard", "big", "large", "overview", "all"),
            d(
                "Everything", TileSize.LARGE, TilePalette.WALLPAPER, TileSurface.TONAL, 5,
                row(
                    1,
                    TileBlock(BlockType.CLOCK, weight = 2, bind = "time.clock", style = BlockStyle.PLAIN),
                    TileBlock(BlockType.BADGE, bind = "weather.now", shape = TileShape.SUNNY, style = BlockStyle.SHAPE, tone = TileTone.TERTIARY),
                ),
                row(
                    1,
                    TileBlock(BlockType.RING, bind = "health.steps", label = "Steps"),
                    TileBlock(BlockType.COUNTDOWN, weight = 2, bind = "calendar.next", tone = TileTone.SECONDARY),
                ),
                row(
                    1,
                    TileBlock(BlockType.METRIC, bind = "server.status", label = "Server", tone = TileTone.PRIMARY),
                    TileBlock(BlockType.AI, weight = 2, bind = "ai.text", text = "One useful line about my day from what this widget shows.", every = 60),
                ),
            ),
        ),
    )

    fun byId(id: String?): Starter? = all.firstOrNull { it.id == id }

    /** A fresh copy of [s]'s design to edit: its own id, now as its time. */
    fun instantiate(s: Starter): TileDesign {
        val now = System.currentTimeMillis()
        return s.design.copy(id = TileDesign.newId(), createdAt = now, updatedAt = now)
    }

    /** The starter whose words best match [prompt], for the draft shown while the AI works. */
    fun match(prompt: String): Starter? {
        val p = prompt.lowercase()
        return all.maxByOrNull { s -> s.keywords.count { p.contains(it) } }?.takeIf { s -> s.keywords.any { p.contains(it) } }
    }

    /** A starter made for [size] (in dp), for previews of an empty size. */
    fun forSize(size: DpSize): TileDesign {
        val d = WidgetDims(size)
        val ts = when {
            d.rows <= 1 && d.cols <= 3 -> TileSize.MINI
            d.rows <= 1 -> TileSize.BAR
            d.cols <= 3 -> TileSize.SMALL
            d.rows <= 2 -> TileSize.WIDE
            else -> TileSize.LARGE
        }
        return all.first { it.design.size == ts }.design
    }
}
