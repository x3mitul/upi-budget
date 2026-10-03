package dev.mitul.upibudget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.mitul.upibudget.R

/** Cool sage paper, pine ink, one moss accent. Marigold marks set-aside money, brick marks overspending. */
data class Palette(
    val paper: Color, val ink: Color, val inkSoft: Color, val moss: Color, val onMoss: Color,
    val marigold: Color, val brick: Color, val mist: Color,
)

private val LightPalette = Palette(
    paper = Color(0xFFF1F4F2), ink = Color(0xFF10241F), inkSoft = Color(0xFF5C6F68), moss = Color(0xFF2F6B57), onMoss = Color(0xFFF1F4F2),
    marigold = Color(0xFFD9962B), brick = Color(0xFFB8432F), mist = Color(0xFFD9E2DD),
)
private val DarkPalette = Palette(
    paper = Color(0xFF0D1713), ink = Color(0xFFE4EDE8), inkSoft = Color(0xFF8CA096), moss = Color(0xFF6FBF9F), onMoss = Color(0xFF0D1713),
    marigold = Color(0xFFE8B04A), brick = Color(0xFFE27A66), mist = Color(0xFF22332C),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val pal: Palette @Composable get() = LocalPalette.current

@OptIn(ExperimentalTextApi::class)
private fun face(w: Int) = Font(R.font.bricolage, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
private val Bricolage = FontFamily(face(400), face(500), face(600), face(700))

private fun t(size: Int, weight: Int, line: Int, track: Double = 0.0) = TextStyle(
    fontFamily = Bricolage, fontSize = size.sp, fontWeight = FontWeight(weight), lineHeight = line.sp,
    letterSpacing = track.sp, fontFeatureSettings = "tnum",
)

private val Type = Typography(
    displayLarge = t(56, 600, 60, -2.0), displayMedium = t(44, 600, 48, -1.5), displaySmall = t(36, 600, 40, -1.0),
    headlineMedium = t(28, 600, 32, -0.6), headlineSmall = t(24, 600, 28, -0.4),
    titleLarge = t(22, 600, 28, -0.2), titleMedium = t(17, 600, 24), titleSmall = t(15, 600, 20),
    bodyLarge = t(17, 400, 25), bodyMedium = t(15, 400, 22), bodySmall = t(13, 400, 18),
    labelLarge = t(15, 600, 20), labelMedium = t(13, 500, 18), labelSmall = t(12, 500, 16),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val scheme = (if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()).copy(
        primary = p.moss, onPrimary = p.onMoss, primaryContainer = p.mist, onPrimaryContainer = p.ink,
        secondary = p.marigold, background = p.paper, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink, surfaceVariant = p.mist, onSurfaceVariant = p.inkSoft,
        surfaceContainer = p.paper, surfaceContainerHigh = p.paper, surfaceContainerHighest = p.mist, surfaceContainerLow = p.paper,
        outline = p.inkSoft, outlineVariant = p.mist, error = p.brick,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Type, shapes = Shapes) {
            Surface(Modifier.fillMaxSize(), color = p.paper, contentColor = p.ink, content = content)
        }
    }
}
