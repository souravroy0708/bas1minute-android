package com.bas1minute.sync.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bas1minute.sync.R

// Same palette as the website
val Ink = Color(0xFF0F1235)
val Ink2 = Color(0xFF181D4C)
val Ink3 = Color(0xFF232963)
val Line = Color(0xFF323A7C)
val Haldi = Color(0xFFFFC21A)
val Violet = Color(0xFF8F99FF)
val Coral = Color(0xFFFF8A65)
val Mint = Color(0xFF5ED6A0)
val MintDark = Color(0xFF0B7A55)
/** Dark mode switch, set by Bas1MinuteTheme. Surface colours below read it, so screens adapt automatically. */
object ThemeState { var dark by androidx.compose.runtime.mutableStateOf(false) }

// Theme-aware surfaces and text (the dark hero, brand yellow and chart colours stay the same in both themes)
val Paper: Color get() = if (ThemeState.dark) Color(0xFF0A0C22) else Color(0xFFF4F5FA)
val PaperLine: Color get() = if (ThemeState.dark) Color(0xFF2B3063) else Color(0xFFDCDFEC)
val Muted: Color get() = if (ThemeState.dark) Color(0xFFA9AECB) else Color(0xFF4A4F6A)
val CardBg: Color get() = if (ThemeState.dark) Color(0xFF161A42) else Color.White
val TextMain: Color get() = if (ThemeState.dark) Color(0xFFEEF0FF) else Color(0xFF0F1235)
val MutedOnDark = Color(0xFFBCC1E0)
val Blue = Color(0xFF3446C8)
val Danger = Color(0xFFB42318)

val Display = FontFamily(
    Font(R.font.anek_bold, FontWeight.Bold),
    Font(R.font.anek_extrabold, FontWeight.ExtraBold),
)
val Body = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_bold, FontWeight.Bold),
)
val Devanagari = FontFamily(Font(R.font.anek_devanagari, FontWeight.SemiBold))

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 50.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 33.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
)

@Composable
fun Bas1MinuteTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    ThemeState.dark = dark
    MaterialTheme(
        colorScheme = if (dark) androidx.compose.material3.darkColorScheme(
            primary = Violet,
            onPrimary = Ink,
            secondary = Haldi,
            onSecondary = Ink,
            background = Color(0xFF0A0C22),
            onBackground = Color(0xFFEEF0FF),
            surface = Color(0xFF161A42),
            onSurface = Color(0xFFEEF0FF),
            surfaceVariant = Color(0xFF0A0C22),
            outline = Color(0xFF2B3063),
        ) else lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            secondary = Haldi,
            onSecondary = Ink,
            background = Paper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Paper,
            outline = PaperLine,
        ),
        typography = AppTypography,
    ) {
        // Default text colour follows the theme (texts without an explicit colour stay readable in dark mode)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides TextMain, content = content)
    }
}
