package com.promptshift.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PromptShiftColors {
    val ObsidianBg = Color(0xFF0A0A0D)
    val CarbonSurface = Color(0xFF141419)
    val CarbonElevated = Color(0xFF1B1B22)
    val MetalBorder = Color(0xFF282830)
    val GtCrimson = Color(0xFFD61F26)
    val GtCrimsonGlow = Color(0xFFFF2E38)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextMuted = Color(0xFF9A9AA6)
    val CopiedEmerald = Color(0xFF22C55E)
    val CopiedEmeraldBg = Color(0xFF132E1E)
    val QuotaWarnRed = Color(0xFFFF4D4F)
    val QuotaWarnBg = Color(0xFF2E1416)
}

private val PromptShiftColorScheme = darkColorScheme(
    primary = PromptShiftColors.GtCrimson,
    onPrimary = PromptShiftColors.TextPrimary,
    secondary = PromptShiftColors.CopiedEmerald,
    onSecondary = PromptShiftColors.ObsidianBg,
    background = PromptShiftColors.ObsidianBg,
    onBackground = PromptShiftColors.TextPrimary,
    surface = PromptShiftColors.CarbonSurface,
    onSurface = PromptShiftColors.TextPrimary,
    surfaceVariant = PromptShiftColors.CarbonElevated,
    onSurfaceVariant = PromptShiftColors.TextMuted,
    outline = PromptShiftColors.MetalBorder,
    error = PromptShiftColors.QuotaWarnRed,
    onError = PromptShiftColors.TextPrimary,
)

private val PromptShiftTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = 1.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun PromptShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PromptShiftColorScheme,
        typography = PromptShiftTypography,
        content = content,
    )
}
