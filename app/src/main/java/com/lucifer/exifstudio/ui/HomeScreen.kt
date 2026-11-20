package com.lucifer.exifstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucifer.exifstudio.data.HistoryItem
import com.lucifer.exifstudio.vm.StudioState

@Composable
fun HomeScreen(
    state: StudioState,
    onPick: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenLibrary: () -> Unit,
    onStripAll: () -> Unit,
    onOpenHistory: (HistoryItem) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tok.Page)
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            "EXIFSTUDIO",
            style = MaterialTheme.typography.labelSmall,
            color = Tok.Accent2
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("影迹", style = MaterialTheme.typography.displayLarge, color = Tok.Text1)
            Spacer(Modifier.width(9.dp))
            Text(
                "ExifStudio",
                style = MaterialTheme.typography.titleLarge,
                color = Tok.Text3
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "读取 · 改写 · 伪装照片的拍摄参数",
            style = MaterialTheme.typography.bodyMedium,
            color = Tok.Text3
        )

        Spacer(Modifier.height(22.dp))

        if (state.hasPhoto) {
            PhotoPreviewCard(state, onOpenEditor)
            Spacer(Modifier.height(16.dp))
        }

        PrimaryButton(
            text = if (state.hasPhoto) "更换照片" else "选择照片",
            icon = Icons.Rounded.AddPhotoAlternate,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPick
        )

        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "最近编辑",
                    style = MaterialTheme.typography.titleMedium,
                    color = Tok.Text2,
                    modifier = Modifier.weight(1f)
                )
                Text("${state.history.size} 张", style = Mono, color = Tok.Text3)
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.history) { item ->
                    HistoryThumb(item) { onOpenHistory(item) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.CameraAlt,
                title = "设备参数库",
                subtitle = "${state.deviceCount} 款机型",
                onClick = onOpenLibrary
            )
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.CleaningServices,
                title = "清除 EXIF",
                subtitle = if (state.hasPhoto) "保留画质" else "先选一张照片",
                enabled = state.hasPhoto,
                tint = Tok.Danger,
                onClick = onStripAll
            )
        }

        Spacer(Modifier.height(14.dp))

        GlassCard(radius = Tok.RCard, padding = Tok.L) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Map,
                    contentDescription = null,
                    tint = Tok.Accent2,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "位置可自选：地图长按定点，搜索后自动跳转，坐标按 WGS-84 写入",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tok.Text3
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Text(
            "所有元数据仅保存在设备本地",
            style = MaterialTheme.typography.labelSmall,
            color = Tok.Text4
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun PhotoPreviewCard(state: StudioState, onClick: () -> Unit) {
    val uri = state.photoUri ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .glass(Tok.RCard)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Tok.RCard))
        )

        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Tok.RCard))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Tok.Bg0.copy(alpha = 0.85f))
                    )
                )
        )

        if (state.changedCount > 0) {
            Badge(
                text = "已改 ${state.changedCount} 项",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Tok.L)
        ) {
            Text(
                state.profile.describeDevice(),
                style = MaterialTheme.typography.titleMedium,
                color = Tok.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.profile.lensModel.isBlank()) "未读取到镜头信息"
                else state.profile.lensModel,
                style = Mono,
                color = Tok.Text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "继续编辑",
                style = MaterialTheme.typography.labelLarge,
                color = Tok.Accent2
            )
        }
    }
}

/** 强调色胶囊徽标 */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(Tok.RChip))
            .background(Tok.Accent.copy(alpha = 0.22f))
            .border(1.dp, Tok.Accent.copy(alpha = 0.45f), RoundedCornerShape(Tok.RChip))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Tok.Text1
        )
    }
}

@Composable
private fun HistoryThumb(item: HistoryItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(Tok.RThumb))
                .background(Tok.Glass)
                .border(1.dp, Tok.Stroke, RoundedCornerShape(Tok.RThumb))
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.label.ifBlank { "已编辑" },
            style = Mono,
            color = Tok.Text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Tok.Accent2,
    onClick: () -> Unit
) {
    Column(
        modifier
            .glass(Tok.RCard)
            .clickable(enabled = enabled) { onClick() }
            .padding(Tok.L)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (enabled) 0.16f else 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else Tok.Text4,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Tok.Text1 else Tok.Text3
        )
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = Mono,
            color = Tok.Text3,
            maxLines = 2
        )
    }
}
