package com.lucifer.exifstudio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计 Token —— 暗夜液态玻璃。
 * 所有颜色/圆角/间距集中在这里，改主题只改这一个文件。
 */
object Tok {
    // 背景（不是纯黑，带极轻冷调）
    val Bg0 = Color(0xFF050506)
    val Bg1 = Color(0xFF0B0C0F)

    // 玻璃面
    val Glass = Color(0x0FFFFFFF)
    val GlassHi = Color(0x1AFFFFFF)
    val Stroke = Color(0x14FFFFFF)
    val Highlight = Color(0x2EFFFFFF)

    // 文字四级
    val Text1 = Color(0xEBFFFFFF)
    val Text2 = Color(0x9EFFFFFF)
    val Text3 = Color(0x61FFFFFF)
    val Text4 = Color(0x33FFFFFF)

    // 强调（面积 < 5%）
    val Accent = Color(0xFF6E8BFF)
    val Accent2 = Color(0xFF7DD3FC)
    val Danger = Color(0xFFFF6B6B)
    val Success = Color(0xFF4ADE80)

    // 圆角
    val RCard = 24.dp
    val RInner = 16.dp
    val RChip = 12.dp
    val RSheet = 28.dp
    val RButton = 18.dp

    // 间距
    val Page = 20.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
}

private val AppColorScheme = darkColorScheme(
    primary = Tok.Accent,
    onPrimary = Color(0xFF08090F),
    primaryContainer = Tok.Accent.copy(alpha = 0.22f),
    onPrimaryContainer = Tok.Accent2,
    secondary = Tok.Accent2,
    onSecondary = Color(0xFF06121A),
    background = Tok.Bg0,
    onBackground = Tok.Text1,
    surface = Color(0xFF0D0F14),
    onSurface = Tok.Text1,
    surfaceVariant = Color(0xFF14171E),
    onSurfaceVariant = Tok.Text2,
    outline = Tok.Stroke,
    outlineVariant = Tok.Stroke,
    error = Tok.Danger,
    onError = Color(0xFF1A0505)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 32.sp, lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 23.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp
    )
)

/** EXIF 参数一律等宽，强调精密感 */
val Mono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    letterSpacing = 0.sp
)

@Composable
fun ExifStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
