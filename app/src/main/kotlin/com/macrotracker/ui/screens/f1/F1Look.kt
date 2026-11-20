package com.macrotracker.ui.screens.f1

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.macrotracker.data.f1.TyreStint
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.LocalRoomColor
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import java.io.File

// The F1 page's own pieces. It stands in Home's rooms, tinted red, and sets its headlines
// in a race cut of the same family the rest of Home uses: Google Sans Flex leaned over,
// narrow and heavy with square ends, where everything else on Home is round.

val F1Tone = HomeTone.F1

/** Tyres in F1's own colours: soft red, medium yellow, hard white, intermediate green, wet blue. */
object Tyre {
    val Soft = Color(0xFFFF5A52)
    val Medium = Color(0xFFFFC94D)
    val Hard = Color(0xFFE8E8E8)
    val Inter = Color(0xFF5CC98A)
    val Wet = Color(0xFF5AB4F5)
    val Unknown = Color(0xFF8A8A8A)

    fun colour(compound: String): Color = when (compound.uppercase()) {
        "SOFT" -> Soft
        "MEDIUM" -> Medium
        "HARD" -> Hard
        "INTERMEDIATE" -> Inter
        "WET" -> Wet
        else -> Unknown
    }

    fun letter(compound: String): String = when (compound.uppercase()) {
        "SOFT" -> "S"
        "MEDIUM" -> "M"
        "HARD" -> "H"
        "INTERMEDIATE" -> "I"
        "WET" -> "W"
        else -> "?"
    }

    val All = listOf("SOFT", "MEDIUM", "HARD", "INTERMEDIATE", "WET")
}

/** Timing colours, as every timing screen has them: purple the session's best, green a personal best. */
val LapPurple = Color(0xFFC38AF0)
val LapGreen = Color(0xFF5CC98A)
val GainGreen = Color(0xFF5CC98A)
val LossRed = Color(0xFFFF6369)
val FlagYellow = Color(0xFFFFC94D)
val FlagRed = Color(0xFFFF4B45)
val FlagGreen = Color(0xFF5CC98A)
val Gold = Color(0xFFFFD166)
val Silver = Color(0xFFC9D1D9)
val Bronze = Color(0xFFD9925B)

fun medal(pos: Int?): Color? = when (pos) {
    1 -> Gold
    2 -> Silver
    3 -> Bronze
    else -> null
}

/** The colour of a lap or sector by its mark from the feed ("ob", "pb", "y", blank). */
fun lapMarkColour(mark: String, plain: Color = TextPrimary): Color = when (mark) {
    "ob" -> LapPurple
    "pb" -> LapGreen
    "y" -> FlagYellow
    else -> plain
}

fun flagColour(flag: String): Color = when (flag) {
    "yellow", "sc", "vsc" -> FlagYellow
    "red" -> FlagRed
    else -> FlagGreen
}

/** A team's colour from its `#rrggbb`; grey when it will not parse. */
fun teamColour(hex: String): Color = runCatching { Color((if (hex.startsWith("#")) hex else "#$hex").toColorInt()) }.getOrDefault(Color(0xFF888888))

private val RaceFamily: FontFamily by lazy {
    val file = listOf("/product/fonts/GoogleSansFlex-Regular.ttf", "/system/fonts/GoogleSansFlex-Regular.ttf")
        .map(::File)
        .firstOrNull { it.canRead() && runCatching { Typeface.Builder(it).build() != null }.getOrDefault(false) }
        ?: return@lazy FontFamily.Default
    fun cut(weight: FontWeight, wght: Int) = Font(
        file = file,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(wght),
            FontVariation.width(78f),
            FontVariation.slant(-10f),
            FontVariation.Setting("ROND", 0f),
        ),
    )
    runCatching { FontFamily(cut(FontWeight.Bold, 760), cut(FontWeight.ExtraBold, 840), cut(FontWeight.Black, 920)) }
        .getOrDefault(FontFamily.Default)
}

/** The race cut: leaning, narrow, heavy. Where the phone has no Flex, its own bold italic. */
val F1Display: TextStyle by lazy {
    TextStyle(
        fontFamily = RaceFamily,
        fontWeight = FontWeight.Black,
        fontStyle = if (RaceFamily == FontFamily.Default) FontStyle.Italic else FontStyle.Normal,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.3).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

/** Timing figures: the Home display face, tabular, a touch lighter so a column of them stays calm. */
val F1Figure: TextStyle by lazy { HealthDisplay.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }

private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/**
 * A driver's face in a circle with a ring of the team's colour. [image] may hold several
 * candidates ("a|b"); each is tried in turn, and with none the code stands in.
 */
@Composable
fun DriverFace(image: String?, code: String, colour: Color, size: Dp, modifier: Modifier = Modifier, ring: Boolean = true) {
    val context = LocalContext.current
    val urls = remember(image) { image.orEmpty().split('|').map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    var at by remember(urls) { mutableIntStateOf(0) }
    val url = urls.getOrNull(at)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colour.copy(alpha = 0.22f))
            .then(if (ring) Modifier.border(if (size >= 44.dp) 2.dp else 1.5.dp, colour, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Text(
                code.take(3),
                style = F1Display,
                color = colour,
                fontSize = (size.value * 0.3f).coerceIn(9f, 20f).sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (url == null) {
            fallback()
        } else {
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).size(256).crossfade(false).setHeader("User-Agent", UA).build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(Modifier.fillMaxSize())
                    is AsyncImagePainter.State.Error -> {
                        if (at < urls.lastIndex) LaunchedEffect(url) { at++ }
                        if (at >= urls.lastIndex) fallback() else Box(Modifier.fillMaxSize())
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

/** A team's badge on a tile of its colour; its code when there is no badge to load. */
@Composable
fun TeamBadge(logoUrl: String?, code: String, colour: Color, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(colour.copy(alpha = 0.18f))
            .padding(size * 0.16f),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Text(code, style = F1Display, color = colour, fontSize = (size.value * 0.26f).coerceIn(9f, 18f).sp, maxLines = 1, softWrap = false)
        }
        if (logoUrl.isNullOrBlank()) {
            fallback()
        } else {
            val request = remember(logoUrl) {
                ImageRequest.Builder(context).data(logoUrl).size(192).crossfade(false).setHeader("User-Agent", UA)
                    .apply { if (logoUrl.endsWith(".svg", ignoreCase = true)) decoderFactory(SvgDecoder.Factory()) }
                    .build()
            }
            SubcomposeAsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(Modifier.fillMaxSize())
                    is AsyncImagePainter.State.Error -> fallback()
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

/** A tyre as timing screens draw it: a ring in the compound's colour round its letter. */
@Composable
fun TyreDot(compound: String, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val c = Tyre.colour(compound)
    Box(
        modifier = modifier.size(size).border(2.5.dp, c, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Tyre.letter(compound), style = HealthDisplay, color = c, fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A car's stints as one bar: each compound's laps in its colour, as wide as the laps it ran. */
@Composable
fun StintBar(stints: List<TyreStint>, maxLaps: Int, modifier: Modifier = Modifier) {
    val total = stints.sumOf { it.laps }.coerceAtLeast(1)
    val room = LocalRoomColor.current
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth((total.toFloat() / maxLaps.coerceAtLeast(1)).coerceIn(0.06f, 1f)).height(18.dp).clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            stints.forEach { s ->
                Box(
                    modifier = Modifier
                        .weight(s.laps.coerceAtLeast(1).toFloat())
                        .height(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Tyre.colour(s.compound).copy(alpha = if (s.fresh) 1f else 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (s.laps >= 6) Text("${s.laps}", style = HealthDisplay, color = room, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** The last finishes, oldest left: a win gold, a podium silver, points grey, nothing a dash. */
@Composable
fun FormRow(form: List<Int?>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        form.forEach { p ->
            val (fill, ink) = when {
                p == 1 -> Gold to Color(0xFF14181D)
                p != null && p <= 3 -> Silver.copy(alpha = 0.9f) to Color(0xFF14181D)
                p != null && p <= 10 -> Color.White.copy(alpha = 0.14f) to TextPrimary
                else -> Color.White.copy(alpha = 0.06f) to TextTertiary
            }
            Box(
                Modifier.size(width = 19.dp, height = 19.dp).clip(RoundedCornerShape(6.dp)).background(fill),
                contentAlignment = Alignment.Center,
            ) {
                Text(p?.toString() ?: "–", style = HealthDisplay, color = ink, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A small word on a tint: "Live", "Replay", "Provisional". */
@Composable
fun F1Tag(text: String, colour: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text,
        style = HealthDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = if (filled) Color(0xFF14181D) else colour,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.clip(CircleShape).background(if (filled) colour else colour.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** A swatch of the team's colour down the start of a timing row. */
@Composable
fun TeamSwatch(colour: Color, modifier: Modifier = Modifier, height: Dp = 26.dp) {
    Box(modifier.width(4.dp).height(height).clip(RoundedCornerShape(2.dp)).background(colour))
}

private val ISO = mapOf(
    "Australia" to "AU", "Austria" to "AT", "Azerbaijan" to "AZ", "Bahrain" to "BH", "Belgium" to "BE", "Brazil" to "BR",
    "Canada" to "CA", "China" to "CN", "France" to "FR", "Germany" to "DE", "Hungary" to "HU", "Italy" to "IT", "Japan" to "JP",
    "Korea" to "KR", "Malaysia" to "MY", "Mexico" to "MX", "Monaco" to "MC", "Netherlands" to "NL", "Portugal" to "PT",
    "Qatar" to "QA", "Saudi Arabia" to "SA", "Singapore" to "SG", "South Africa" to "ZA", "Spain" to "ES", "Turkey" to "TR",
    "UAE" to "AE", "United Arab Emirates" to "AE", "UK" to "GB", "United Kingdom" to "GB", "USA" to "US", "United States" to "US",
)

/** The country's flag as an emoji, or nothing for a country the list lacks. */
fun flagEmoji(country: String): String {
    val iso = ISO[country.trim()] ?: return ""
    return iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** "1:23" or "1:02:03" from seconds, for a session clock. */
fun clockText(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).toLong()
    val h = s / 3600
    val m = s / 60 % 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

val F1SubText = TextSecondary
