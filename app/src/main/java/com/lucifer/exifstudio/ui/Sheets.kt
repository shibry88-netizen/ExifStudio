package com.lucifer.exifstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucifer.exifstudio.data.Brand
import com.lucifer.exifstudio.data.Camera
import com.lucifer.exifstudio.data.Device

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    brands: List<Brand>,
    selected: Device?,
    onDismiss: () -> Unit,
    onPick: (Device) -> Unit
) {
    var filter by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val devices = remember(filter, brands) {
        brands
            .filter { filter == null || it.id == filter }
            .flatMap { it.devices }
            .sortedWith(compareByDescending<Device> { it.year }.thenBy { it.name })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tok.Sheet,
        scrimColor = Tok.Bg0.copy(alpha = 0.6f)
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = Tok.Page)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择机型", style = MaterialTheme.typography.headlineMedium, color = Tok.Text1)
                    Text(
                        "近两年主流机型 · 含前后置镜头参数",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tok.Text3
                    )
                }
                Text("${devices.size} 款", style = Mono, color = Tok.Text3)
            }

            Spacer(Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("全部", filter == null) { filter = null } }
                items(brands) { brand ->
                    Pill(brand.zh, filter == brand.id) { filter = brand.id }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxWidth()) {
                items(devices) { device ->
                    DeviceRow(device, device.id == selected?.id) { onPick(device) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(Tok.RInner, Frostness.Thin)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium,
                color = Tok.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${device.year} · ${device.rear.size} 后置 / ${device.front.size} 前置",
                style = MaterialTheme.typography.bodySmall,
                color = Tok.Text3
            )
        }
        if (isSelected) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Tok.Accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Tok.Accent2,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Text(
                "${device.lensCount} 摄",
                style = MaterialTheme.typography.labelSmall,
                color = Tok.Text3
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LensPickerSheet(
    device: Device?,
    selected: Camera?,
    onDismiss: () -> Unit,
    onPick: (Camera) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (device == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tok.Sheet,
        scrimColor = Tok.Bg0.copy(alpha = 0.6f)
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = Tok.Page)
        ) {
            Text(device.name, style = MaterialTheme.typography.headlineMedium, color = Tok.Text1)
            Text(
                "选择镜头 · 光圈/焦距/ISO/白平衡会一并写入",
                style = MaterialTheme.typography.bodySmall,
                color = Tok.Text3
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(Modifier.fillMaxWidth()) {
                if (device.rear.isNotEmpty()) {
                    item { GroupLabel("后置") }
                    items(device.rear) { cam ->
                        LensCard(cam, cam == selected) { onPick(cam) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (device.front.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        GroupLabel("前置")
                    }
                    items(device.front) { cam ->
                        LensCard(cam, cam == selected) { onPick(cam) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Tok.Accent2,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun LensCard(cam: Camera, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(Tok.RInner, Frostness.Thin)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cam.role, style = MaterialTheme.typography.titleMedium, color = Tok.Text1)
            Spacer(Modifier.width(8.dp))
            Text(cam.summary, style = Mono, color = Tok.Accent2)
            Spacer(Modifier.weight(1f))
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Tok.Accent2,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            cam.sensor,
            style = MaterialTheme.typography.bodySmall,
            color = Tok.Text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "ISO ${cam.iso} · WB ${if (cam.wb == "auto") "自动" else "${cam.wb}K"} · ${cam.shutter}s",
            style = MaterialTheme.typography.labelSmall,
            color = Tok.Text3
        )
    }
}
