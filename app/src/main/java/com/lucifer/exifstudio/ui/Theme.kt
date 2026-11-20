package com.lucifer.exifstudio.ui

import androidx.compose.animation.core.CubicBezierEasing
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
 * 设计 Token —— 暗夜液态玻璃（对齐设计师交付的 ExifTokens）。
 * 玻璃只有三层：底胚 6% 白 + 顶内高光 18% + 外 1px 8% 亮边，再叠 -20px/60px 柔投影。
 * 再多一层就浑河。
 */
object Tok {

    // ── 色彩 ──────────────────────────────────────────
    val Bg0 = Color(0xFF050506)
    val Bg1 = Color(0xFF0B0C0F)

    val Glass = Color(0x0FFFFFFF)        // rgba(255,255,255,.06)
    val GlassStrong = Color(0x17FFFFFF)  // .09
    val Stroke = Color(0x14FFFFFF)       // .08
    val Highlight = Color(0x2EFFFFFF)    // .18

    val Text1 = Color(0xEBFFFFFF)        // .92
    val Text2 = Color(0x9EFFFFFF)        // .62
    val Text3 = Color(0x61FFFFFF)        // .38
    val Text4 = Color(0x33FFFFFF)        // .20

    val Accent = Color(0xFF6E8BFF)
    val Accent2 = Color(0xFF7DD3FC)
    val AccentLight = Color(0xFF7A94FF)
    val AccentDeep = Color(0xFF607CF0)
    val Danger = Color(0xFFFF7A7A)
    val Success = Color(0xFF5AD6A0)

    // ── 圆角 ──────────────────────────────────────────
    val RCard = 24.dp
    val RInner = 16.dp
    val RChip = 999.dp
    val RSheet = 32.dp
    val RButton = 20.dp
    val RCta = 26.dp
    val RThumb = 18.dp

    // ── 间距（4/8 网格）──────────────────────────────
    val Page = 20.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp

    // ── 动效 ──────────────────────────────────────────
    /** 统一缓动：Apple 式减速曲线 */
    val Ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val DurMicro = 140      // 按压 / 勾选
    const val DurContainer = 320  // 抽屉 / 面板
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

/** Display 30/34 · Title 20/26 · Headline 17/22 · Body 15/24 · Label 13/18 · Caption 12/16 · Micro 11/14 */
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 30.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
    )
)

/** EXIF 数值：等宽 + tabular-nums，强调精密感 */
val Mono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    letterSpacing = (-0.1).sp,
    fontFeatureSettings = "tnum"
)

@Composable
fun ExifStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(Tok.RInner),
            medium = RoundedCornerShape(Tok.RButton),
            large = RoundedCornerShape(Tok.RCard),
            extraLarge = RoundedCornerShape(Tok.RSheet)
        ),
        content = content
    )
}
