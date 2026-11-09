package com.cropadvisor.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cropadvisor.R

/**
 * "Jade & Champagne" premium theme — deep jade green with soft champagne-gold accents.
 * Reading a colour inside a composable makes it follow light / dark mode automatically.
 */
object Palette {
    var dark by mutableStateOf(false)

    private fun c(light: Long, night: Long) = if (dark) Color(night) else Color(light)

    // ---- fixed brand colours (used on photos / gradients, same in both modes) ----
    val ForestFixed = Color(0xFF0A4A3F)
    val ForestDeep = Color(0xFF04201B)
    val Gold = Color(0xFFC8A660)
    val GoldLight = Color(0xFFE6CF9A)
    val GoldDeep = Color(0xFF8C7136)
    val OnGold = Color(0xFF1E1708)
    val MintSoftFixed = Color(0xFFD8EEE7)
    val MintFixed = Color(0xFF3FBF9A)

    // ---- adaptive ----
    val Forest get() = c(0xFF0A4A3F, 0xFF6FD3B5)        // primary: deep jade (light) / light jade (dark)
    val OnForest get() = c(0xFFFFFFFF, 0xFF03211B)
    val Leaf get() = c(0xFF0F7A62, 0xFF7CDCC0)          // secondary green text / accents
    val Mint get() = c(0xFF23A77F, 0xFF3FBF9A)
    val MintSoft get() = c(0xFFE2F2EC, 0xFF0F2A24)
    val Cream get() = c(0xFFF4F7F5, 0xFF060E0C)         // page background
    val Card get() = c(0xFFFFFFFF, 0xFF0E1A17)
    val CardAlt get() = c(0xFFEBF1EE, 0xFF142420)
    val Ink get() = c(0xFF10201C, 0xFFE8F0ED)
    val InkSoft get() = c(0xFF5A6B66, 0xFF94A8A2)
    val Line get() = c(0xFFDDE6E2, 0xFF1E2E2A)
    val GoldText get() = c(0xFF8C6E2A, 0xFFE2C88E)
    val GoldSoft get() = c(0xFFF6EFDF, 0xFF262013)
    val Amber get() = c(0xFFC9861A, 0xFFF0B54D)
    val AmberSoft get() = c(0xFFFBF1DD, 0xFF2D2410)
    val Terracotta get() = c(0xFFC0492F, 0xFFEE7A60)
    val TerracottaSoft get() = c(0xFFFBE6E0, 0xFF361A13)
    val Sky get() = c(0xFF2F6F99, 0xFF83BFE4)
    val SkySoft get() = c(0xFFE3EEF6, 0xFF13263A)

    val hero get() = if (dark) Brush.linearGradient(listOf(Color(0xFF020B09), Color(0xFF08201B), Color(0xFF0E3A31)))
                     else Brush.linearGradient(listOf(Color(0xFF032A23), Color(0xFF0A4A3F), Color(0xFF146B5A)))
    val goldBrush = Brush.linearGradient(listOf(Color(0xFFEAD7A6), Color(0xFFC8A660), Color(0xFFA88A47)))
    val scrim = Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0xE604201B)))
}

/** Colour for a severity key: none / mild / moderate / severe. */
fun severityColor(key: String) = when (key) {
    "mild" -> Palette.Leaf
    "moderate" -> Palette.Amber
    "severe" -> Palette.Terracotta
    else -> Palette.Mint
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_bold, FontWeight.ExtraBold),
)
val SerifDisplay = FontFamily(Font(R.font.dmserif_regular, FontWeight.Normal))

private val type = Typography(
    displaySmall = TextStyle(fontFamily = SerifDisplay, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = SerifDisplay, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    if (Palette.dark != dark) Palette.dark = dark
    val scheme = if (dark) darkColorScheme(
        primary = Palette.Forest, onPrimary = Palette.OnForest, background = Palette.Cream,
        surface = Palette.Card, onSurface = Palette.Ink, onBackground = Palette.Ink,
        surfaceVariant = Palette.CardAlt, onSurfaceVariant = Palette.InkSoft, outline = Palette.Line,
        error = Palette.Terracotta, surfaceContainer = Palette.Card, surfaceContainerHigh = Palette.Card,
    ) else lightColorScheme(
        primary = Palette.Forest, onPrimary = Palette.OnForest, background = Palette.Cream,
        surface = Palette.Card, onSurface = Palette.Ink, onBackground = Palette.Ink,
        surfaceVariant = Palette.CardAlt, onSurfaceVariant = Palette.InkSoft, outline = Palette.Line,
        error = Palette.Terracotta, surfaceContainer = Palette.Card, surfaceContainerHigh = Palette.Card,
    )
    MaterialTheme(
        colorScheme = scheme, typography = type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}
