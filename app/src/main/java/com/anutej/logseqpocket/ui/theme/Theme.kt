package com.anutej.logseqpocket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode { System, Light, Dark }

/** Extra tokens Material3 has no slot for. */
@Immutable
data class PocketColors(
    val codeBackground: Color,
    val codeText: Color,
    val guideLine: Color,
    val linkText: Color,
    val missingLink: Color,
    val tagChip: Color,
    val tagChipText: Color,
    val highlight: Color,
    val know: Color,
    val again: Color,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.Teal300,
    onPrimary = Palette.Teal900,
    primaryContainer = Palette.Teal800,
    onPrimaryContainer = Palette.Teal100,
    secondary = Palette.Slate300,
    onSecondary = Palette.Navy900,
    secondaryContainer = Palette.Navy700,
    onSecondaryContainer = Palette.Slate100,
    tertiary = Palette.Amber300,
    background = Palette.Navy900,
    onBackground = Palette.Slate100,
    surface = Palette.Navy900,
    onSurface = Palette.Slate100,
    surfaceVariant = Palette.Navy800,
    onSurfaceVariant = Palette.Slate300,
    surfaceContainerLowest = Palette.Navy950,
    surfaceContainerLow = Palette.Navy850,
    surfaceContainer = Palette.Navy850,
    surfaceContainerHigh = Palette.Navy800,
    surfaceContainerHighest = Palette.Navy700,
    outline = Palette.Navy600,
    outlineVariant = Palette.Navy700,
    error = Palette.Rose300,
)

private val LightScheme = lightColorScheme(
    primary = Palette.Teal700,
    onPrimary = Color.White,
    primaryContainer = Palette.Teal100,
    onPrimaryContainer = Palette.Teal900,
    secondary = Palette.Ink600,
    onSecondary = Color.White,
    secondaryContainer = Palette.Paper200,
    onSecondaryContainer = Palette.Ink900,
    tertiary = Palette.Amber700,
    background = Palette.Paper50,
    onBackground = Palette.Ink900,
    surface = Palette.Paper50,
    onSurface = Palette.Ink900,
    surfaceVariant = Palette.Paper100,
    onSurfaceVariant = Palette.Ink600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Paper100,
    surfaceContainer = Palette.Paper100,
    surfaceContainerHigh = Palette.Paper200,
    surfaceContainerHighest = Palette.Paper300,
    outline = Palette.Paper300,
    outlineVariant = Palette.Paper200,
    error = Palette.Rose700,
)

private val DarkExtras = PocketColors(
    codeBackground = Palette.Navy950,
    codeText = Palette.Teal200,
    guideLine = Palette.Navy700,
    linkText = Palette.Teal300,
    missingLink = Palette.Slate300.copy(alpha = 0.7f),
    tagChip = Palette.Navy800,
    tagChipText = Palette.Teal200,
    highlight = Palette.Amber300.copy(alpha = 0.35f),
    know = Palette.Teal300,
    again = Palette.Amber300,
)

private val LightExtras = PocketColors(
    codeBackground = Palette.Paper200,
    codeText = Palette.Teal900,
    guideLine = Palette.Paper300,
    linkText = Palette.Teal700,
    missingLink = Palette.Ink600.copy(alpha = 0.7f),
    tagChip = Palette.Teal100,
    tagChipText = Palette.Teal900,
    highlight = Palette.Amber300.copy(alpha = 0.45f),
    know = Palette.Teal700,
    again = Palette.Amber700,
)

val LocalPocketColors = staticCompositionLocalOf { LightExtras }
val LocalReadingSize = staticCompositionLocalOf { ReadingSize.Medium }

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LogseqPocketTheme(
    themeMode: ThemeMode = ThemeMode.System,
    readingSize: ReadingSize = ReadingSize.Medium,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalPocketColors provides if (dark) DarkExtras else LightExtras,
        LocalReadingSize provides readingSize,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = pocketTypography(),
            shapes = PocketShapes,
            content = content,
        )
    }
}

object PocketTheme {
    val colors: PocketColors @Composable get() = LocalPocketColors.current
    val readingSize: ReadingSize @Composable get() = LocalReadingSize.current
}
