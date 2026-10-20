package com.anutej.logseqpocket.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Reading size chosen in Settings; scales note body text only (UI chrome follows system font scale). */
enum class ReadingSize(val bodySp: Float, val lineHeightSp: Float) {
    Small(15f, 22f), Medium(16.5f, 25f), Large(18.5f, 28f)
}

val CodeFontFamily: FontFamily = FontFamily.Monospace

internal fun pocketTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

fun noteBodyStyle(size: ReadingSize): TextStyle = TextStyle(
    fontSize = size.bodySp.sp,
    lineHeight = size.lineHeightSp.sp,
    letterSpacing = 0.1.sp,
)
