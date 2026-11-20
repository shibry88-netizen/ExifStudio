package com.lucifer.exifstudio.ui

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 背景：深空黑 + 三团流动光斑。玻璃感由光决定，不由边框决定。 */
@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tok.Bg0, Tok.Bg1)))
    ) {
        Blob(Tok.Accent.copy(alpha = 0.68f), 380.dp, (-90).dp, (-40).dp)
        Blob(Color(0xFF9678FF).copy(alpha = 0.38f), 340.dp, 200.dp, 120.dp)
        Blob(Tok.Accent2.copy(alpha = 0.42f), 360.dp, (-60).dp, 520.dp)
        content()
    }
}

@Composable
private fun Blob(color: Color, size: Dp, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .blur(90.dp)
            .background(
                Brush.radialGradient(listOf(color, color.copy(alpha = 0f))),
                CircleShape
            )
    )
}

/**
 * 液态玻璃材质：
 * 底胚（6% 白渐变）+ 顶部内高光 18% + 外 1px 8% 亮边 + 大而柔的环境投影。
 */
fun Modifier.glass(
    radius: Dp = Tok.RCard,
    tint: Color = Tok.Glass
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(
            elevation = 24.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black,
            spotColor = Color.Black
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to tint.copy(alpha = 0.10f),
                0.44f to tint.copy(alpha = 0.028f),
                1f to tint.copy(alpha = 0.05f)
            )
        )
        .border(1.dp, Tok.Stroke, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Tok.RCard,
    padding: Dp = Tok.L,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(radius)
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
                .glass()
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
            color = if (value.isBlank()) Tok.Text4 else valueColor,
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

/** 主 CTA：54dp 高 / 26dp 圆角 / 竖向渐变 + 强调色辉光 */
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
                alpha = if (enabled) 1f else 0.45f
            }
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Tok.Accent,
                spotColor = Tok.Accent
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Tok.AccentLight, Tok.AccentDeep)))
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
            .glass(Tok.RButton)
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

/** 全圆 Chip */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) Tok.Accent.copy(alpha = 0.20f) else Tok.Glass
    val stroke = if (selected) Tok.Accent.copy(alpha = 0.60f) else Tok.Stroke
    val fg = if (selected) Color.White else Tok.Text2
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(Tok.RChip))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(Tok.RChip))
            .clickable { onClick() }
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
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
