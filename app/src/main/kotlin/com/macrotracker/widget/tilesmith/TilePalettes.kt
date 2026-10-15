package com.macrotracker.widget.tilesmith

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.macrotracker.R
import androidx.glance.color.ColorProvider as DayNightColorProvider

/**
 * The Material 3 colour roles a Tilesmith widget paints with. [res] is the `tsm_*` colour
 * resource the Wallpaper palette resolves to: the system's own M3 tokens on Android 14+, the
 * wallpaper's tonal palettes by tone on 12-13, the Ocean scheme before that (see
 * the `tilesmith_colors.xml` resources). The order matches [TilePalette]'s arrays.
 */
enum class TileRole(val res: Int) {
    PRIMARY(R.color.tsm_primary),
    ON_PRIMARY(R.color.tsm_on_primary),
    PRIMARY_CONTAINER(R.color.tsm_primary_container),
    ON_PRIMARY_CONTAINER(R.color.tsm_on_primary_container),
    SECONDARY(R.color.tsm_secondary),
    ON_SECONDARY(R.color.tsm_on_secondary),
    SECONDARY_CONTAINER(R.color.tsm_secondary_container),
    ON_SECONDARY_CONTAINER(R.color.tsm_on_secondary_container),
    TERTIARY(R.color.tsm_tertiary),
    ON_TERTIARY(R.color.tsm_on_tertiary),
    TERTIARY_CONTAINER(R.color.tsm_tertiary_container),
    ON_TERTIARY_CONTAINER(R.color.tsm_on_tertiary_container),
    ERROR(R.color.tsm_error),
    ON_ERROR(R.color.tsm_on_error),
    ERROR_CONTAINER(R.color.tsm_error_container),
    ON_ERROR_CONTAINER(R.color.tsm_on_error_container),
    SURFACE(R.color.tsm_surface),
    ON_SURFACE(R.color.tsm_on_surface),
    ON_SURFACE_VARIANT(R.color.tsm_on_surface_variant),
    SURFACE_CONTAINER_LOWEST(R.color.tsm_surface_container_lowest),
    SURFACE_CONTAINER_LOW(R.color.tsm_surface_container_low),
    SURFACE_CONTAINER(R.color.tsm_surface_container),
    SURFACE_CONTAINER_HIGH(R.color.tsm_surface_container_high),
    SURFACE_CONTAINER_HIGHEST(R.color.tsm_surface_container_highest),
    OUTLINE(R.color.tsm_outline),
    OUTLINE_VARIANT(R.color.tsm_outline_variant),
    INVERSE_SURFACE(R.color.tsm_inverse_surface),
    INVERSE_ON_SURFACE(R.color.tsm_inverse_on_surface),
}

/**
 * A widget's colours: the wallpaper's (Material You, following the launcher), or one of ten
 * fixed M3 schemes generated with Google's material-color-utilities (SchemeVibrant from each
 * seed; Sand is TonalSpot and Mono is SchemeMonochrome). Each array holds the [TileRole]s in
 * order, light then dark, as `0xRRGGBB`.
 *
 * Widgets take a role as a Glance [ColorProvider] ([provider]): a resource for the wallpaper,
 * a day/night pair otherwise, so the launcher flips light and dark on its own.
 */
enum class TilePalette(
    val id: String,
    val label: String,
    val seed: Long,
    private val light: IntArray?,
    private val dark: IntArray?,
) {
    WALLPAPER("wallpaper", "Wallpaper", 0xFF6D7DD6, null, null),
    OCEAN(
        "ocean", "Ocean", 0xFF1E6FD9,
        light = intArrayOf(0x005CBD, 0xFFFFFF, 0xD7E2FF, 0x004591, 0x585C7E, 0xFFFFFF, 0xDFE0FF, 0x404565, 0x615889, 0xFFFFFF, 0xE6DEFF, 0x494070, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xF9F9FF, 0x171C25, 0x414753, 0xFFFFFF, 0xF1F3FF, 0xEAEDFA, 0xE5E8F5, 0xDFE2EF, 0x727785, 0xC1C6D6, 0x2C303A, 0xEDF0FD),
        dark = intArrayOf(0xACC7FF, 0x002F67, 0x004591, 0xD7E2FF, 0xC0C4EB, 0x2A2E4D, 0x404565, 0xDFE0FF, 0xCABFF8, 0x322958, 0x494070, 0xE6DEFF, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x0F131C, 0xDFE2EF, 0xC1C6D6, 0x0A0E17, 0x171C25, 0x1B2029, 0x262A33, 0x31353E, 0x8B919F, 0x414753, 0xDFE2EF, 0x2C303A),
    ),
    BERRY(
        "berry", "Berry", 0xFFC2185B,
        light = intArrayOf(0xBB0055, 0xFFFFFF, 0xFFD9DF, 0x8F003F, 0x82524E, 0xFFFFFF, 0xFFDAD6, 0x663B37, 0x8A4F37, 0xFFFFFF, 0xFFDBCE, 0x6E3822, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xFFF8F7, 0x26181B, 0x574145, 0xFFFFFF, 0xFFF0F1, 0xFFE8EB, 0xFCE2E6, 0xF7DCE0, 0x8A7175, 0xDDBFC4, 0x3C2C2F, 0xFFECEE),
        dark = intArrayOf(0xFFB1C2, 0x66002B, 0x8F003F, 0xFFD9DF, 0xF5B7B2, 0x4C2522, 0x663B37, 0xFFDAD6, 0xFFB599, 0x52220E, 0x6E3822, 0xFFDBCE, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x1D1012, 0xF7DCE0, 0xDDBFC4, 0x170B0D, 0x26181B, 0x2A1C1F, 0x352629, 0x413034, 0xA58A8F, 0x574145, 0xF7DCE0, 0x3C2C2F),
    ),
    FOREST(
        "forest", "Forest", 0xFF2E7D32,
        light = intArrayOf(0x006E1B, 0xFFFFFF, 0x71FF75, 0x005312, 0x426650, 0xFFFFFF, 0xC4ECD0, 0x2A4E39, 0x256958, 0xFFFFFF, 0xACF0DA, 0x005141, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xF3FCEC, 0x161E14, 0x3F4A3C, 0xFFFFFF, 0xEDF6E7, 0xE8F1E1, 0xE2EBDB, 0xDCE5D6, 0x6F7A6B, 0xBECAB8, 0x2A3328, 0xEAF4E4),
        dark = intArrayOf(0x00E543, 0x003909, 0x005312, 0x71FF75, 0xA8D0B5, 0x133724, 0x2A4E39, 0xC4ECD0, 0x91D4BE, 0x00382C, 0x005141, 0xACF0DA, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x0E150C, 0xDCE5D6, 0xBECAB8, 0x081008, 0x161E14, 0x1A2218, 0x242C22, 0x2F372C, 0x889484, 0x3F4A3C, 0xDCE5D6, 0x2A3328),
    ),
    SUNSET(
        "sunset", "Sunset", 0xFFF4511E,
        light = intArrayOf(0xB02E00, 0xFFFFFF, 0xFFDBD1, 0x872100, 0x7F5539, 0xFFFFFF, 0xFFDCC7, 0x643E24, 0x80561F, 0xFFFFFF, 0xFFDDB9, 0x643E08, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xFFF8F6, 0x271814, 0x58423B, 0xFFFFFF, 0xFFF1ED, 0xFFE9E4, 0xFFE2DB, 0xF9DCD5, 0x8C716A, 0xE0C0B7, 0x3D2C28, 0xFFEDE8),
        dark = intArrayOf(0xFFB5A0, 0x5F1500, 0x872100, 0xFFDBD1, 0xF2BB99, 0x492810, 0x643E24, 0xFFDCC7, 0xF4BC7C, 0x472A00, 0x643E08, 0xFFDDB9, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x1E100C, 0xF9DCD5, 0xE0C0B7, 0x180B07, 0x271814, 0x2B1C18, 0x372622, 0x42312C, 0xA78A83, 0x58423B, 0xF9DCD5, 0x3D2C28),
    ),
    LEMON(
        "lemon", "Lemon", 0xFFF9C80E,
        light = intArrayOf(0x745B00, 0xFFFFFF, 0xFFE08A, 0x574400, 0x675F30, 0xFFFFFF, 0xEFE3A9, 0x4E471B, 0x616123, 0xFFFFFF, 0xE7E79A, 0x49490C, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xFFF8F0, 0x201B0D, 0x4E4631, 0xFFFFFF, 0xFEF2DB, 0xF8EDD5, 0xF3E7D0, 0xEDE1CA, 0x80765F, 0xD2C5AA, 0x363020, 0xFBF0D8),
        dark = intArrayOf(0xF1C100, 0x3D2F00, 0x574400, 0xFFE08A, 0xD2C78F, 0x373106, 0x4E471B, 0xEFE3A9, 0xCACB80, 0x323200, 0x49490C, 0xE7E79A, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x181306, 0xEDE1CA, 0xD2C5AA, 0x120E03, 0x201B0D, 0x241F11, 0x2F291A, 0x3A3424, 0x9A9077, 0x4E4631, 0xEDE1CA, 0x363020),
    ),
    LAVENDER(
        "lavender", "Lavender", 0xFF7E57C2,
        light = intArrayOf(0x7B00FD, 0xFFFFFF, 0xEBDCFF, 0x5D00C2, 0x6C5778, 0xFFFFFF, 0xF4DAFF, 0x533F5F, 0x79507A, 0xFFFFFF, 0xFFD6FD, 0x5F3961, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xFEF7FF, 0x1E1A24, 0x4A4453, 0xFFFFFF, 0xF9F0FF, 0xF4EBFA, 0xEEE5F4, 0xE8DFEE, 0x7B7484, 0xCCC3D5, 0x332E39, 0xF7EEFD),
        dark = intArrayOf(0xD4BBFF, 0x41008B, 0x5D00C2, 0xEBDCFF, 0xD8BDE4, 0x3C2947, 0x533F5F, 0xF4DAFF, 0xE8B7E7, 0x46234A, 0x5F3961, 0xFFD6FD, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x15121C, 0xE8DFEE, 0xCCC3D5, 0x100C16, 0x1E1A24, 0x221E28, 0x2C2833, 0x37333E, 0x958E9E, 0x4A4453, 0xE8DFEE, 0x332E39),
    ),
    MINT(
        "mint", "Mint", 0xFF00A389,
        light = intArrayOf(0x006B59, 0xFFFFFF, 0x00FFD7, 0x005143, 0x386664, 0xFFFFFF, 0xBBECE8, 0x1F4E4C, 0x1B6871, 0xFFFFFF, 0xA9EEF8, 0x004F57, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xEEFDF6, 0x121E1A, 0x3A4A45, 0xFFFFFF, 0xE9F7F1, 0xE3F1EB, 0xDDEBE5, 0xD7E6E0, 0x697B75, 0xB8CBC4, 0x26332F, 0xE6F4EE),
        dark = intArrayOf(0x00DFBD, 0x00382D, 0x005143, 0x00FFD7, 0xA0CFCC, 0x003735, 0x1F4E4C, 0xBBECE8, 0x8DD2DC, 0x00363C, 0x004F57, 0xA9EEF8, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x091612, 0xD7E6E0, 0xB8CBC4, 0x05100D, 0x121E1A, 0x16221E, 0x202C29, 0x2B3733, 0x83958E, 0x3A4A45, 0xD7E6E0, 0x26332F),
    ),
    RACING(
        "racing", "Racing", 0xFFE10600,
        light = intArrayOf(0xC00500, 0xFFFFFF, 0xFFDAD4, 0x930300, 0x80543D, 0xFFFFFF, 0xFFDBCB, 0x653D28, 0x835423, 0xFFFFFF, 0xFFDCBF, 0x673D0D, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xFFF8F6, 0x271815, 0x58413E, 0xFFFFFF, 0xFFF0EE, 0xFFE9E5, 0xFFE2DD, 0xF9DCD7, 0x8C716C, 0xDFBFBA, 0x3D2C29, 0xFFEDEA),
        dark = intArrayOf(0xFFB4A8, 0x680200, 0x930300, 0xFFDAD4, 0xF4BA9E, 0x4B2714, 0x653D28, 0xFFDBCB, 0xF9BA80, 0x4B2800, 0x673D0D, 0xFFDCBF, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x1E100D, 0xF9DCD7, 0xDFBFBA, 0x180B09, 0x271815, 0x2B1C19, 0x372623, 0x42312E, 0xA78A85, 0x58413E, 0xF9DCD7, 0x3D2C29),
    ),
    SAND(
        "sand", "Sand", 0xFFB08D57,
        light = intArrayOf(0x7D570E, 0xFFFFFF, 0xFFDEAE, 0x604100, 0x6E5B40, 0xFFFFFF, 0xF9DFBB, 0x55442A, 0x4F6442, 0xFFFFFF, 0xD1EABE, 0x384C2C, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x93000A, 0xFFF8F3, 0x201B13, 0x4F4539, 0xFFFFFF, 0xFEF2E5, 0xF8ECDF, 0xF2E6DA, 0xECE1D4, 0x817567, 0xD2C4B4, 0x362F27, 0xFBEFE2),
        dark = intArrayOf(0xF1BE6D, 0x432C00, 0x604100, 0xFFDEAE, 0xDCC3A1, 0x3D2E16, 0x55442A, 0xF9DFBB, 0xB6CEA3, 0x223517, 0x384C2C, 0xD1EABE, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x18130B, 0xECE1D4, 0xD2C4B4, 0x120D07, 0x201B13, 0x241F17, 0x2F2921, 0x3A342B, 0x9B8F80, 0x4F4539, 0xECE1D4, 0x362F27),
    ),
    MONO(
        "mono", "Mono", 0xFF5F6368,
        light = intArrayOf(0x000000, 0xE2E2E2, 0x3B3B3B, 0xFFFFFF, 0x5E5E5E, 0xFFFFFF, 0xD4D4D4, 0x1B1B1B, 0x3B3B3B, 0xE2E2E2, 0x747474, 0xFFFFFF, 0xBA1A1A, 0xFFFFFF, 0xFFDAD6, 0x410002, 0xF9F9F9, 0x1B1B1B, 0x474747, 0xFFFFFF, 0xF3F3F3, 0xEEEEEE, 0xE8E8E8, 0xE2E2E2, 0x777777, 0xC6C6C6, 0x303030, 0xF1F1F1),
        dark = intArrayOf(0xFFFFFF, 0x1B1B1B, 0xD4D4D4, 0x000000, 0xC6C6C6, 0x1B1B1B, 0x474747, 0xE2E2E2, 0xE2E2E2, 0x1B1B1B, 0x919191, 0x000000, 0xFFB4AB, 0x690005, 0x93000A, 0xFFDAD6, 0x131313, 0xE2E2E2, 0xC6C6C6, 0x0E0E0E, 0x1B1B1B, 0x1F1F1F, 0x2A2A2A, 0x353535, 0x919191, 0x474747, 0xE2E2E2, 0x303030),
    ),
    ;

    /**
     * [role] as Glance paints it. The wallpaper's roles are resource providers, the same kind
     * Glance's own dynamic theme is built from; its public `GlanceTheme.colors` has no
     * surface-container roles, which every tile here needs, hence the suppression.
     */
    @SuppressLint("RestrictedApi")
    fun provider(role: TileRole): ColorProvider {
        val l = light ?: return ColorProvider(role.res)
        val d = dark ?: return ColorProvider(role.res)
        return DayNightColorProvider(day = Color(0xFF000000 or l[role.ordinal].toLong()), night = Color(0xFF000000 or d[role.ordinal].toLong()))
    }

    /** [role]'s light and dark ARGB, or null for the wallpaper (a resource: [TileRole.res]). */
    fun dayNight(role: TileRole): Pair<Int, Int>? {
        val l = light ?: return null
        val d = dark ?: return null
        return (0xFF000000.toInt() or l[role.ordinal]) to (0xFF000000.toInt() or d[role.ordinal])
    }

    /**
     * [role] as an ARGB colour in [dark] or light mode: for the app's own screens (swatches, the
     * studio's backdrop). The wallpaper's is read from the same resources the widget uses.
     */
    fun color(context: Context, role: TileRole, dark: Boolean): Color {
        val table = if (dark) this.dark else this.light
        if (table != null) return Color(0xFF000000 or table[role.ordinal].toLong())
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return Color(context.createConfigurationContext(config).getColor(role.res))
    }

    /** Whether this palette follows the wallpaper on this phone (Android 12+). */
    val dynamic: Boolean get() = light == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    companion object {
        fun byId(id: String?): TilePalette? {
            val key = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == key || it.label.lowercase() == key }
                ?: if (key in setOf("dynamic", "material you", "system", "auto")) WALLPAPER else null
        }
    }
}
