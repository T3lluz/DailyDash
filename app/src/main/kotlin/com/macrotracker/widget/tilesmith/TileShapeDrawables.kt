package com.macrotracker.widget.tilesmith

import com.macrotracker.R

/** Each [TileShape] as the vector widgets draw (`res/drawable/tsm_shape_*.xml`, generated from [M3Shapes]). */
object TileShapeDrawables {
    fun res(shape: TileShape): Int = when (shape) {
        TileShape.CIRCLE -> R.drawable.tsm_shape_circle
        TileShape.SQUARE -> R.drawable.tsm_shape_square
        TileShape.SLANTED -> R.drawable.tsm_shape_slanted
        TileShape.ARCH -> R.drawable.tsm_shape_arch
        TileShape.FAN -> R.drawable.tsm_shape_fan
        TileShape.ARROW -> R.drawable.tsm_shape_arrow
        TileShape.SEMI_CIRCLE -> R.drawable.tsm_shape_semi_circle
        TileShape.OVAL -> R.drawable.tsm_shape_oval
        TileShape.PILL -> R.drawable.tsm_shape_pill
        TileShape.TRIANGLE -> R.drawable.tsm_shape_triangle
        TileShape.DIAMOND -> R.drawable.tsm_shape_diamond
        TileShape.CLAM_SHELL -> R.drawable.tsm_shape_clam_shell
        TileShape.PENTAGON -> R.drawable.tsm_shape_pentagon
        TileShape.GEM -> R.drawable.tsm_shape_gem
        TileShape.SUNNY -> R.drawable.tsm_shape_sunny
        TileShape.VERY_SUNNY -> R.drawable.tsm_shape_very_sunny
        TileShape.COOKIE_4 -> R.drawable.tsm_shape_cookie4
        TileShape.COOKIE_6 -> R.drawable.tsm_shape_cookie6
        TileShape.COOKIE_7 -> R.drawable.tsm_shape_cookie7
        TileShape.COOKIE_9 -> R.drawable.tsm_shape_cookie9
        TileShape.COOKIE_12 -> R.drawable.tsm_shape_cookie12
        TileShape.GHOSTISH -> R.drawable.tsm_shape_ghostish
        TileShape.CLOVER_4 -> R.drawable.tsm_shape_clover4
        TileShape.CLOVER_8 -> R.drawable.tsm_shape_clover8
        TileShape.BURST -> R.drawable.tsm_shape_burst
        TileShape.SOFT_BURST -> R.drawable.tsm_shape_soft_burst
        TileShape.BOOM -> R.drawable.tsm_shape_boom
        TileShape.SOFT_BOOM -> R.drawable.tsm_shape_soft_boom
        TileShape.FLOWER -> R.drawable.tsm_shape_flower
        TileShape.PUFFY -> R.drawable.tsm_shape_puffy
        TileShape.PUFFY_DIAMOND -> R.drawable.tsm_shape_puffy_diamond
        TileShape.PIXEL_CIRCLE -> R.drawable.tsm_shape_pixel_circle
        TileShape.PIXEL_TRIANGLE -> R.drawable.tsm_shape_pixel_triangle
        TileShape.BUN -> R.drawable.tsm_shape_bun
        TileShape.HEART -> R.drawable.tsm_shape_heart
    }
}
