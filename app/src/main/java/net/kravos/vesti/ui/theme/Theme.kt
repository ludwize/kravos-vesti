package net.kravos.vesti.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.kravos.vesti.data.ThemeMode

/** Newspaper palette: paper / ink / one red accent. */
data class Palette(
    val dark: Boolean,
    val ink: Color,
    val dim: Color,
    val rule: Color,
    val accent: Color,
    val live: Color,
    val placeholder: Color,
)

val LocalPalette = staticCompositionLocalOf {
    Palette(false, Color(0xFF16161A), Color(0xFF6E6E73), Color(0xFFE4E0D6), Color(0xFFB3261E), Color(0xFFD7261E), Color(0xFFEDE9DF))
}

private val Paper = Palette(false, Color(0xFF16161A), Color(0xFF6B6B70), Color(0xFFE4E0D6), Color(0xFFB3261E), Color(0xFFD7261E), Color(0xFFEDE9DF))
private val Night = Palette(true, Color(0xFFEDEAE4), Color(0xFF8E8E93), Color(0xFF2A2A2C), Color(0xFFFF6B5E), Color(0xFFFF453A), Color(0xFF1E1E20))

private val Light = lightColorScheme(
    primary = Paper.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DEDA),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFEDE9DF),
    onSecondaryContainer = Paper.ink,
    background = Color(0xFFFBFAF6),
    onBackground = Paper.ink,
    surface = Color(0xFFFBFAF6),
    onSurface = Paper.ink,
    surfaceVariant = Color(0xFFF1EEE6),
    onSurfaceVariant = Paper.dim,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F5F0),
    surfaceContainer = Color(0xFFF3F0E9),
    surfaceContainerHigh = Color(0xFFEDE9DF),
    surfaceContainerHighest = Color(0xFFE6E2D8),
    outline = Color(0xFFB9B4A8),
    outlineVariant = Paper.rule,
)

private val Dark = darkColorScheme(
    primary = Night.accent,
    onPrimary = Color(0xFF2B0000),
    primaryContainer = Color(0xFF4A1410),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondaryContainer = Color(0xFF26262A),
    onSecondaryContainer = Night.ink,
    background = Color(0xFF0F0F10),
    onBackground = Night.ink,
    surface = Color(0xFF0F0F10),
    onSurface = Night.ink,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Night.dim,
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF18181A),
    surfaceContainerHigh = Color(0xFF1F1F22),
    surfaceContainerHighest = Color(0xFF29292C),
    outline = Color(0xFF5A5A5F),
    outlineVariant = Night.rule,
)

/** Headlines and reader body use the system serif (Noto Serif on Android) for the printed-paper feel. */
object Fonts {
    val Serif = FontFamily.Serif
}

private val Type = Typography().let { t ->
    t.copy(
        displaySmall = TextStyle(fontFamily = Fonts.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = Fonts.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        headlineSmall = TextStyle(fontFamily = Fonts.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        titleLarge = TextStyle(fontFamily = Fonts.Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
        titleMedium = TextStyle(fontFamily = Fonts.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    )
}

@Composable
fun VestiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    CompositionLocalProvider(LocalPalette provides if (dark) Night else Paper) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}
