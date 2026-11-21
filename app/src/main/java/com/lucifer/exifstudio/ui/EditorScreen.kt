package com.lucifer.exifstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucifer.exifstudio.data.ExifEngine
import com.lucifer.exifstudio.vm.StudioState
import com.lucifer.exifstudio.vm.StudioViewModel
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: StudioState,
    vm: StudioViewModel,
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenLens: () -> Unit,
    onOpenMap: () -> Unit
) {
    val profile = state.profile

    var editIso by remember { mutableStateOf<String?>(null) }
    var editShutter by remember { mutableStateOf<String?>(null) }
    var editFocal by remember { mutableStateOf<String?>(null) }
    var editFNumber by remember { mutableStateOf<String?>(null) }
    var editDate by remember { mutableStateOf<String?>(null) }
    var showWb by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tok.Bg0.copy(alpha = 0f), Tok.Bg0.copy(alpha = 0.42f))))
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 132.dp)
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    BackBar(
                        title = profile.describeDevice(),
                        subtitle = if (state.camera == null) {
                            "未匹配机型"
                        } else {
                            state.camera.role + " · " + state.camera.summary
                        },
                        onBack = onBack
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = Tok.Page)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .glass(Tok.RCard, Frostness.UltraThin)
                    ) {
                        AsyncImage(
                            model = state.photoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(Tok.RCard))
                        )

                        if (state.changedCount > 0) {
                            Badge(
                                text = "已改 ${state.changedCount} 项",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                            )
                        }

                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Tok.Bg0.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "${profile.width} × ${profile.height}",
                                style = Mono,
                                color = Tok.Text2
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }

            item {
                Column(Modifier.padding(horizontal = Tok.Page)) {
                    SectionCard(
                        title = "设备信息",
                        subtitle = "决定照片「出自哪台机器」",
                        action = {
                            Text(
                                if (state.device == null) "去选择" else "更换",
                                style = MaterialTheme.typography.labelMedium,
                                color = Tok.Accent2,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    ) {
                        KeyValueRow("品牌型号", profile.model, onClick = onOpenDevices)
                        DividerLine()
                        KeyValueRow(
                            "镜头",
                            profile.lensModel,
                            mono = false,
                            onClick = onOpenLens
                        )
                        DividerLine()
                        KeyValueRow("软件", profile.software.ifBlank { "—" })
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }

            item {
                Column(Modifier.padding(horizontal = Tok.Page)) {
                    SectionCard(
                        title = "曝光参数",
                        subtitle = "点击任意项可手动改写"
                    ) {
                        KeyValueRow(
                            "光圈",
                            if (profile.fNumber.isBlank()) "" else "F${profile.fNumber}",
                            valueColor = Tok.Accent2,
                            onClick = { editFNumber = profile.fNumber }
                        )
                        DividerLine()
                        KeyValueRow(
                            "快门",
                            if (profile.exposureTime.isBlank()) "" else "${profile.exposureTime} s",
                            onClick = { editShutter = profile.exposureTime }
                        )
                        DividerLine()
                        KeyValueRow(
                            "ISO",
                            profile.iso,
                            onClick = { editIso = profile.iso }
                        )
                        DividerLine()
                        KeyValueRow(
                            "焦距",
                            if (profile.focalLength.isBlank()) "" else
                                "${profile.focalLength}mm · 等效 ${profile.focal35}mm",
                            onClick = { editFocal = profile.focalLength }
                        )
                        DividerLine()
                        KeyValueRow(
                            "白平衡",
                            profile.describeWb(),
                            onClick = { showWb = true }
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }

            item {
                Column(Modifier.padding(horizontal = Tok.Page)) {
                    SectionCard(
                        title = "时间与位置",
                        subtitle = "位置会写成 WGS-84 经纬度"
                    ) {
                        KeyValueRow(
                            "拍摄时间",
                            profile.dateTime,
                            onClick = { editDate = profile.dateTime }
                        )
                        DividerLine()
                        KeyValueRow(
                            "地点",
                            if (profile.hasLocation) {
                                String.format(
                                    Locale.US, "%.5f, %.5f",
                                    profile.latitude ?: 0.0, profile.longitude ?: 0.0
                                )
                            } else {
                                ""
                            },
                            valueColor = Tok.Accent2,
                            onClick = onOpenMap
                        )
                        if (profile.address.isNotBlank()) {
                            DividerLine()
                            KeyValueRow("地址", profile.address, mono = false)
                        }
                        if (profile.hasLocation) {
                            DividerLine()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Tok.L, vertical = 10.dp)
                            ) {
                                GhostButton(
                                    text = "清除位置",
                                    tint = Tok.Danger,
                                    onClick = { vm.clearLocation() }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        // 底部保存条
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Tok.Bg0.copy(alpha = 0f), Tok.Bg0.copy(alpha = 0.55f), Tok.Bg0.copy(alpha = 0.78f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = Tok.Page, vertical = Tok.L)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.changedCount > 0) {
                    GhostButton(text = "重置", onClick = { vm.reset() })
                }
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        text = if (state.busy) "处理中…" else "保存为新照片",
                        icon = if (state.busy) null else Icons.Rounded.Save,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.save() }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "原图不会被修改，生成副本存入相册 Pictures/影迹",
                style = MaterialTheme.typography.labelSmall,
                color = Tok.Text3
            )
        }
    }

    editIso?.let { initial ->
        TextEditDialog(
            title = "ISO 感光度",
            initial = initial,
            placeholder = "100",
            hint = "常见的原生档位：50 / 100 / 200 / 400 / 800",
            onDismiss = { editIso = null },
            onConfirm = {
                vm.setIso(it)
                editIso = null
            }
        )
    }
    editShutter?.let { initial ->
        TextEditDialog(
            title = "快门速度",
            initial = initial,
            placeholder = "1/120",
            hint = "写成分数或秒数，例如 1/120、1/1000、2",
            onDismiss = { editShutter = null },
            onConfirm = {
                vm.setShutter(it)
                editShutter = null
            }
        )
    }
    editFocal?.let { initial ->
        TextEditDialog(
            title = "物理焦距（mm）",
            initial = initial,
            placeholder = "6.76",
            hint = "等效焦距由机型决定，这里只改物理值",
            onDismiss = { editFocal = null },
            onConfirm = {
                vm.setFocal(it)
                editFocal = null
            }
        )
    }
    editFNumber?.let { initial ->
        TextEditDialog(
            title = "光圈值",
            initial = initial,
            placeholder = "1.78",
            hint = "只填数字，例如 1.78 / 2.2 / 2.8",
            onDismiss = { editFNumber = null },
            onConfirm = {
                vm.setFNumber(it)
                editFNumber = null
            }
        )
    }
    editDate?.let { initial ->
        TextEditDialog(
            title = "拍摄时间",
            initial = initial,
            placeholder = "2026:11:20 14:30:00",
            hint = "EXIF 格式：yyyy:MM:dd HH:mm:ss",
            mono = true,
            quickLabel = "填入当前时间",
            quickValue = ExifEngine.now(),
            onDismiss = { editDate = null },
            onConfirm = {
                vm.setDateTime(it)
                editDate = null
            }
        )
    }

    if (showWb) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showWb = false },
            sheetState = sheetState,
            containerColor = Tok.Sheet,
            scrimColor = Tok.Bg0.copy(alpha = 0.6f)
        ) {
            Column(Modifier.padding(horizontal = Tok.Page, vertical = Tok.S)) {
                Text("白平衡", style = MaterialTheme.typography.headlineMedium, color = Tok.Text1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "自动档会写入 WhiteBalance=Auto；选定色温则写为手动并记录光源",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tok.Text3
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WbOption("自动", profile.wbAuto && profile.wbKelvin == 0) {
                        vm.setWb(0, true); showWb = false
                    }
                    WbOption("日光 · 5500K", !profile.wbAuto && profile.wbKelvin == 5500) {
                        vm.setWb(5500, false); showWb = false
                    }
                    WbOption("阴天 · 6500K", !profile.wbAuto && profile.wbKelvin == 6500) {
                        vm.setWb(6500, false); showWb = false
                    }
                    WbOption("白炽灯 · 3200K", !profile.wbAuto && profile.wbKelvin == 3200) {
                        vm.setWb(3200, false); showWb = false
                    }
                    WbOption("D50 · 5000K", !profile.wbAuto && profile.wbKelvin == 5000) {
                        vm.setWb(5000, false); showWb = false
                    }
                    WbOption("阴影 · 7000K", !profile.wbAuto && profile.wbKelvin == 7000) {
                        vm.setWb(7000, false); showWb = false
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WbOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tok.RInner)
    val surface = if (selected) {
        Modifier
            .clip(shape)
            .background(Tok.Accent.copy(alpha = 0.26f))
            .border(1.dp, Tok.Accent.copy(alpha = 0.55f), shape)
    } else {
        Modifier.glass(Tok.RInner, Frostness.Thin)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Tok.Accent2 else Tok.Text1,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text("当前", style = MaterialTheme.typography.labelSmall, color = Tok.Accent2)
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    placeholder: String,
    hint: String,
    mono: Boolean = true,
    quickLabel: String? = null,
    quickValue: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.Sheet,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Tok.Text1)
        },
        text = {
            Column {
                GlassField(placeholder, value, { value = it }, mono = mono)
                Spacer(Modifier.height(10.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Tok.Text3)
                if (quickLabel != null && quickValue != null) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = quickLabel, onClick = { value = quickValue })
                }
            }
        },
        confirmButton = {
            Text(
                "确定",
                style = MaterialTheme.typography.labelLarge,
                color = Tok.Accent2,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tok.Accent.copy(alpha = 0.14f))
                    .clickable { onConfirm(value.trim()) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        },
        dismissButton = {
            Text(
                "取消",
                style = MaterialTheme.typography.labelLarge,
                color = Tok.Text3,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    )
}
