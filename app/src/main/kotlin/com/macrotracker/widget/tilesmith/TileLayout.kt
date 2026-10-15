package com.macrotracker.widget.tilesmith

/**
 * Lays a design out at the widget's real size, in dp, before anything is drawn: the frame's
 * padding, then rows by their height weights and cells by their width weights, with M3's even
 * gaps between. A row or cell that would come out smaller than its block can show is dropped
 * (the last row first, then the last cell of a row), so a widget resized smaller loses whole
 * blocks rather than squeezing them until their text clips.
 */
object TileLayout {
    data class Cell(val block: TileBlock, val width: Float, val height: Float)

    data class Row(val height: Float, val cells: List<Cell>)

    data class Plan(val pad: Float, val gap: Float, val rows: List<Row>, val dropped: Int) {
        val cells: List<Cell> get() = rows.flatMap { it.cells }
    }

    fun pad(widthDp: Float, heightDp: Float): Float = when {
        heightDp < 70f -> 6f
        minOf(widthDp, heightDp) < 130f -> 10f
        else -> 12f
    }

    const val GAP = 6f

    /** The least height a block of [type] can show anything useful in. */
    fun minHeight(type: BlockType): Float = when (type) {
        BlockType.RING -> 58f
        BlockType.CHART -> 52f
        BlockType.LIST -> 40f
        BlockType.METRIC, BlockType.BADGE -> 42f
        BlockType.COUNTDOWN, BlockType.AI -> 38f
        BlockType.BAR, BlockType.CLOCK, BlockType.BUTTON -> 34f
        BlockType.TEXT -> 26f
        BlockType.DATE -> 22f
        BlockType.IMAGE -> 40f
        BlockType.CAROUSEL -> 96f
        BlockType.MEDIA -> 56f
    }

    /** The least width a block of [type] can show anything useful in. */
    fun minWidth(type: BlockType): Float = when (type) {
        BlockType.RING, BlockType.BADGE -> 52f
        BlockType.LIST, BlockType.CHART, BlockType.BAR, BlockType.AI -> 70f
        BlockType.CAROUSEL, BlockType.MEDIA -> 120f
        BlockType.IMAGE -> 40f
        else -> 48f
    }

    fun plan(design: TileDesign, widthDp: Float, heightDp: Float): Plan {
        val pad = pad(widthDp, heightDp)
        val innerW = (widthDp - 2 * pad).coerceAtLeast(0f)
        val innerH = (heightDp - 2 * pad).coerceAtLeast(0f)
        var rows = design.rows.filter { it.cells.isNotEmpty() }
        val total = rows.sumOf { it.cells.size }

        // Drop rows from the bottom until every remaining row gets its minimum height.
        while (rows.size > 1) {
            val heights = rowHeights(rows, innerH)
            val ok = rows.indices.all { i -> heights[i] + 0.5f >= rows[i].cells.maxOf { minHeight(it.type) } }
            if (ok) break
            rows = rows.dropLast(1)
        }
        val heights = rowHeights(rows, innerH)
        val planned = rows.mapIndexed { i, r ->
            var cells = r.cells
            while (cells.size > 1) {
                val widths = cellWidths(cells, innerW)
                if (cells.indices.all { c -> widths[c] + 0.5f >= minWidth(cells[c].type) }) break
                cells = cells.dropLast(1)
            }
            val widths = cellWidths(cells, innerW)
            Row(heights[i], cells.mapIndexed { c, b -> Cell(b, widths[c], heights[i]) })
        }
        return Plan(pad, GAP, planned, total - planned.sumOf { it.cells.size })
    }

    private fun rowHeights(rows: List<TileRow>, innerH: Float): List<Float> {
        if (rows.isEmpty()) return emptyList()
        val free = innerH - GAP * (rows.size - 1)
        val sum = rows.sumOf { it.height.coerceIn(1, 3) }.toFloat()
        val raw = rows.map { free * it.height.coerceIn(1, 3) / sum }
        return snap(raw, free)
    }

    private fun cellWidths(cells: List<TileBlock>, innerW: Float): List<Float> {
        val free = innerW - GAP * (cells.size - 1)
        val sum = cells.sumOf { it.weight.coerceIn(1, 4) }.toFloat()
        return snap(cells.map { free * it.weight.coerceIn(1, 4) / sum }, free)
    }

    /** Whole dp each, the last one taking the remainder, so the parts add up to the room exactly. */
    private fun snap(parts: List<Float>, total: Float): List<Float> {
        if (parts.isEmpty()) return parts
        val whole = parts.dropLast(1).map { kotlin.math.floor(it) }
        return whole + (total - whole.sum()).coerceAtLeast(0f)
    }
}
