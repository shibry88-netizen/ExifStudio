package com.lucifer.exifstudio.ui

import androidx.compose.foundation.background
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
import com.lucifer.exifstudio.vm.StudioState

@Composable
fun HomeScreen(
    state: StudioState,
    onPick: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenLibrary: () -> Unit,
    onStripAll: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tok.Page)
    ) {
        Spacer(Modifier.height(28.dp))

        Text(
            "EXIF STUDIO",
            style = MaterialTheme.typography.labelSmall,
            color = Tok.Accent2
        )
        Spacer(Modifier.height(8.dp))
        Text("影迹", style = MaterialTheme.typography.displayLarge, color = Tok.Text1)
        Spacer(Modifier.height(6.dp))
        Text(
            "改写照片的拍摄参数 · 机型 / 镜头 / ISO / 白平衡 / 位置",
            style = MaterialTheme.typography.bodyMedium,
            color = Tok.Text3
        )

        Spacer(Modifier.height(26.dp))

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

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.CameraAlt,
                title = "设备参数库",
                subtitle = "${state.deviceCount} 款机型 · 前后置",
                onClick = onOpenLibrary
            )
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.CleaningServices,
                title = "清除 EXIF",
                subtitle = if (state.hasPhoto) "生成干净副本" else "先选一张照片",
                enabled = state.hasPhoto,
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

        Spacer(Modifier.height(28.dp))
        Text(
            "所有处理都在本机完成，不上传任何照片。",
            style = MaterialTheme.typography.labelSmall,
            color = Tok.Text4
        )
        Spacer(Modifier.height(32.dp))
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

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
                .background(Tok.Accent.copy(alpha = if (enabled) 0.16f else 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Tok.Accent2 else Tok.Text4,
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
            style = MaterialTheme.typography.bodySmall,
            color = Tok.Text3,
            maxLines = 2
        )
    }
}
