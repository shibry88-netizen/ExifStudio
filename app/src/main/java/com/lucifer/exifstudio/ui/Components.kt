package com.lucifer.exifstudio.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 颗粒贴图：128×128 预乘白噪点，一次生成、全站平铺。
 *
 * 这是"磨砂"和"半透明色块"的唯一区别——玻璃背面必须能看见细密的散射，
 * 否则叠在光斑上只会是一层脏灰。
 */
private val Grain: ImageBitmap by lazy {
    val n = 128
    val rnd = Random(0x5EED_2026)
    val px = IntArray(n * n) {
        val a = rnd.nextInt(0, 64)
        (a shl 24) or (a shl 16) or (a shl 8) or a
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** 背景：深空底 + 三团光。没 blur —— 径向渐变本身就是软的，不用离屏模糊省那 4ms。 */
@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tok.Bg0, Tok.Bg1)))
    ) {
        Blob(Tok.Accent.copy(alpha = 0.52f), 380.dp, (-90).dp, (-40).dp)
        Blob(Color(0xFF9678FF).copy(alpha = 0.30f), 340.dp, 200.dp, 120.dp)
        Blob(Tok.Accent2.copy(alpha = 0.34f), 360.dp, (-60).dp, 520.dp)
        content()
    }
}

@Composable
private fun Blob(color: Color, size: Dp, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .background(
                Brush.radialGradient(
                    0.00f to color,
                    0.38f to color.copy(alpha = color.alpha * 0.62f),
                    0.70f to color.copy(alpha = color.alpha * 0.20f),
                    1.00f to color.copy(alpha = 0f)
                ),
                CircleShape
            )
    )
}

/**
 * 磨砂玻璃材质。
 *
 * 三层纯填充，全部走 [drawWithCache] 的 drawBehind：
 *   1. 底胚 —— Frost 按 [frost] 分级给不透明度，光斑透过来只剩柔光
 *   2. 顶缘内高光 —— 竖直渐变，从 17% 白化到透明，止于 55%
 *   3. 极细颗粒 —— 预生成噪点平铺，压到 5%
 *
 * 没有 shadow()、没有 RenderEffect。没有离屏层，就没有黑方块，也没有逐帧模糊。
 */
fun Modifier.glass(
    radius: Dp = Tok.RCard,
    frost: Frostness = Frostness.Regular,
    tint: Color = Tok.Frost
): Modifier {
    val shape = RoundedCornerShape(radius)
    val veil = tint.copy(alpha = frost.veil)
    return this
        .clip(shape)
        .drawWithCache {
            val grainBrush = ShaderBrush(ImageShader(Grain, TileMode.Repeated, TileMode.Repeated))
            onDrawBehind {
                drawRect(veil)
                drawRect(
                    Brush.verticalGradient(
                        0.00f to Tok.FrostTop,
                        0.55f to Color.Transparent
                    )
                )
                drawRect(grainBrush, alpha = 0.05f)
            }
        }
        .border(1.dp, Tok.FrostEdge, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Tok.RCard,
    frost: Frostness = Frostness.Regular,
    padding: Dp = Tok.L,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(radius, frost)
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Tok.Text1)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Tok.Text3
                    )
                }
            }
            action?.invoke()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(frost = Frostness.Thin)
                .padding(vertical = 6.dp),
            content = content
        )
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    valueColor: Color = Tok.Text1,
    labelWidth: Dp = 88.dp,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable { onClick.invoke() }
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        rowModifier.padding(horizontal = Tok.L, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Tok.Text2,
            modifier = Modifier.width(labelWidth)
        )
        Text(
            value.ifBlank { "未设置" },
            style = if (mono) Mono else MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) Tok.Text3 else valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Tok.Text3,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Tok.L)
            .height(1.dp)
            .background(Tok.Stroke)
    )
}

/**
 * 主 CTA：54dp / 26dp 圆角 / 竖向渐变。
 *
 * 辉光不再用有色 shadow —— 那是黑方块的来源。改成在渐变内部压一层暖高光和底部
 * 暗角，视觉上像有光从左上方打过来，只是不往外溢。
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = Tok.DurMicro, easing = Tok.Ease),
        label = "ctaScale"
    )
    val shape = RoundedCornerShape(Tok.RCta)
    Box(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .clip(shape)
            .drawWithCache {
                val fill = Brush.verticalGradient(listOf(Tok.AccentLight, Tok.AccentDeep))
                val sheen = Brush.verticalGradient(
                    0.00f to Color(0x59FFFFFF),
                    0.48f to Color(0x0DFFFFFF),
                    0.52f to Color.Transparent
                )
                val depth = Brush.verticalGradient(
                    0.60f to Color.Transparent,
                    1.00f to Color(0x33000000)
                )
                onDrawBehind {
                    drawRect(fill)
                    drawRect(sheen)
                    drawRect(depth)
                }
            }
            .border(1.dp, Color(0x38FFFFFF), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Tok.Text1,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = Tok.DurMicro, easing = Tok.Ease),
        label = "ghostScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(Tok.RButton, Frostness.Thin)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

/** 全圆 Chip：未选中走最薄一档磨砂，选中走强调色浸染 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Tok.RChip)
    val decoration = if (selected) {
        Modifier
            .clip(shape)
            .background(Tok.Accent.copy(alpha = 0.26f))
            .border(1.dp, Tok.Accent.copy(alpha = 0.55f), shape)
    } else {
        Modifier.glass(Tok.RChip, Frostness.UltraThin)
    }
    Box(
        modifier
            .height(34.dp)
            .then(decoration)
            .clickable { onClick() }
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Tok.Text2
        )
    }
}

@Composable
fun BackBar(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Tok.Page, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier
                .size(34.dp)
                .glass(12.dp, Frostness.UltraThin)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Tok.Text1,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Tok.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Tok.Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
