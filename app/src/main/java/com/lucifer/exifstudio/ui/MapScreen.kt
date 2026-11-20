package com.lucifer.exifstudio.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lucifer.exifstudio.R
import com.lucifer.exifstudio.data.MapTiles
import com.lucifer.exifstudio.data.Place
import com.lucifer.exifstudio.vm.StudioViewModel
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * 地图选点。
 * 交互：长按地图落点 → 逆地理编码出地址 → 「使用此位置」把 WGS-84 经纬度写进 EXIF。
 * 坐标双向转换：高德瓦片是 GCJ-02，EXIF 必须是 WGS-84。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    vm: StudioViewModel,
    initialLat: Double?,
    initialLon: Double?,
    onBack: () -> Unit,
    onConfirm: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var tileKind by remember { mutableStateOf(vm.tileKind) }
    var customUrl by remember { mutableStateOf(vm.customTileUrl) }

    var lat by remember { mutableStateOf(initialLat ?: 39.9087) }
    var lon by remember { mutableStateOf(initialLon ?: 116.3975) }
    var address by remember { mutableStateOf("") }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var showTileSheet by remember { mutableStateOf(false) }
    var showCoordEdit by remember { mutableStateOf(false) }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance()
            .load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        MapView(context).apply {
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setMinZoomLevel(3.0)
            setMaxZoomLevel(19.0)
        }
    }

    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin)
            isDraggable = false
        }
    }

    val receiver = remember {
        object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    val wgs = MapTiles.toWgs(tileKind, p.latitude, p.longitude)
                    lat = wgs[0]
                    lon = wgs[1]
                }
                return true
            }
        }
    }

    LaunchedEffect(Unit) {
        mapView.setTileSource(MapTiles.source(tileKind, customUrl))
        mapView.overlays.add(MapEventsOverlay(receiver))
        mapView.overlays.add(marker)
        val g = MapTiles.toMap(tileKind, lat, lon)
        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(GeoPoint(g[0], g[1]))
    }

    LaunchedEffect(tileKind, customUrl) {
        mapView.setTileSource(MapTiles.source(tileKind, customUrl))
        mapView.invalidate()
    }

    LaunchedEffect(lat, lon, tileKind) {
        val g = MapTiles.toMap(tileKind, lat, lon)
        marker.position = GeoPoint(g[0], g[1])
        mapView.invalidate()
        address = "正在解析地址…"
        address = vm.reverseGeocode(lat, lon).ifBlank { "（未获取到地址）" }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        focusManager.clearFocus()
        scope.launch {
            searching = true
            val list = runCatching { vm.searchPlaces(q) }.getOrDefault(emptyList())
            results = list
            showResults = list.isNotEmpty()
            searching = false
            // 搜索命中：地图小窗口自动跳到该地点
            list.firstOrNull()?.let { place ->
                lat = place.lat
                lon = place.lon
                address = place.subtitle
                val g = MapTiles.toMap(tileKind, place.lat, place.lon)
                mapView.controller.setZoom(16.0)
                mapView.controller.animateTo(GeoPoint(g[0], g[1]))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Tok.Bg0)) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = Tok.Page, vertical = Tok.M)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .glass(Tok.RButton)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Tok.Text3,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索地点或地址",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Tok.Text3
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Tok.Text1),
                            cursorBrush = SolidColor(Tok.Accent2),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Tok.Accent2
                        )
                    } else if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清除",
                            tint = Tok.Text3,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    query = ""
                                    results = emptyList()
                                    showResults = false
                                }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(50.dp)
                        .glass(Tok.RButton)
                        .clickable { showTileSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Layers,
                        contentDescription = "地图源",
                        tint = Tok.Text2,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showResults) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .glass(Tok.RInner)
                        .heightIn(max = 280.dp)
                ) {
                    LazyColumn {
                        items(results) { place ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        lat = place.lat
                                        lon = place.lon
                                        address = place.subtitle
                                        query = place.title
                                        showResults = false
                                        focusManager.clearFocus()
                                        val g = MapTiles.toMap(tileKind, place.lat, place.lon)
                                        mapView.controller.setZoom(16.0)
                                        mapView.controller.animateTo(GeoPoint(g[0], g[1]))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    place.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Tok.Text1,
                                    maxLines = 1
                                )
                                Text(
                                    place.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Tok.Text3,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }

        // 底部信息面板
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = Tok.Page, vertical = Tok.M)
                .glass(Tok.RSheet)
                .padding(Tok.L)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Tok.Success)
                )
                Spacer(Modifier.width(8.dp))
                Text("已选位置", style = MaterialTheme.typography.labelLarge, color = Tok.Text2)
                Spacer(Modifier.weight(1f))
                Text(
                    MapTiles.label(tileKind),
                    style = MaterialTheme.typography.labelSmall,
                    color = Tok.Text3
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                address.ifBlank { "长按地图任意位置落下标记" },
                style = MaterialTheme.typography.bodyMedium,
                color = Tok.Text1,
                maxLines = 2
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "%.6f, %.6f", lat, lon),
                    style = Mono,
                    color = Tok.Accent2
                )
                Spacer(Modifier.width(10.dp))
                Text("WGS-84", style = MaterialTheme.typography.labelSmall, color = Tok.Text4)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(
                    text = "改坐标",
                    icon = Icons.Rounded.MyLocation,
                    onClick = { showCoordEdit = true }
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        text = "使用此位置",
                        icon = Icons.Rounded.Check,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onConfirm(lat, lon, address) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "提示：长按地图定点 · 搜索命中后地图会自动跳转",
                style = MaterialTheme.typography.labelSmall,
                color = Tok.Text4
            )
        }

        // 返回
        Box(
            Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(start = Tok.Page, top = 74.dp)
                .size(40.dp)
                .glass(20.dp)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "返回",
                tint = Tok.Text2,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showTileSheet) {
        TileSourceSheet(
            kind = tileKind,
            customUrl = customUrl,
            onDismiss = { showTileSheet = false },
            onApply = { kind, url ->
                tileKind = kind
                customUrl = url
                vm.tileKind = kind
                vm.customTileUrl = url
                showTileSheet = false
            }
        )
    }

    if (showCoordEdit) {
        CoordEditDialog(
            lat = lat,
            lon = lon,
            onDismiss = { showCoordEdit = false },
            onApply = { newLat, newLon ->
                lat = newLat
                lon = newLon
                showCoordEdit = false
                val g = MapTiles.toMap(tileKind, newLat, newLon)
                mapView.controller.setZoom(16.0)
                mapView.controller.animateTo(GeoPoint(g[0], g[1]))
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TileSourceSheet(
    kind: String,
    customUrl: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    var selected by remember { mutableStateOf(kind) }
    var url by remember { mutableStateOf(customUrl) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tok.Bg1,
        scrimColor = Tok.Bg0.copy(alpha = 0.6f)
    ) {
        Column(Modifier.padding(horizontal = Tok.Page, vertical = Tok.S)) {
            Text("地图源", style = MaterialTheme.typography.titleLarge, color = Tok.Text1)
            Spacer(Modifier.height(6.dp))
            Text(
                "高德瓦片免密钥、中文标注最全；OSM 为国际标准坐标；自建可填自己的瓦片服务。",
                style = MaterialTheme.typography.bodySmall,
                color = Tok.Text3
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("高德", selected == MapTiles.GAODE) { selected = MapTiles.GAODE }
                Pill("OSM", selected == MapTiles.OSM) { selected = MapTiles.OSM }
                Pill("自建", selected == MapTiles.CUSTOM) { selected = MapTiles.CUSTOM }
            }
            if (selected == MapTiles.CUSTOM) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .glass(Tok.RInner)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (url.isEmpty()) {
                        Text(
                            "https://your.server/tiles/{z}/{x}/{y}.png",
                            style = MaterialTheme.typography.bodySmall,
                            color = Tok.Text4
                        )
                    }
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = Mono.copy(color = Tok.Text1),
                        cursorBrush = SolidColor(Tok.Accent2),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "支持 {z}/{x}/{y} 占位符，兼容 XYZ 规范的离线/自建服务",
                    style = MaterialTheme.typography.labelSmall,
                    color = Tok.Text4
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "应用",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onApply(selected, url) }
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CoordEditDialog(
    lat: Double,
    lon: Double,
    onDismiss: () -> Unit,
    onApply: (Double, Double) -> Unit
) {
    var latText by remember { mutableStateOf(String.format(Locale.US, "%.6f", lat)) }
    var lonText by remember { mutableStateOf(String.format(Locale.US, "%.6f", lon)) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.Bg1,
        title = {
            Text("手动输入经纬度", style = MaterialTheme.typography.titleLarge, color = Tok.Text1)
        },
        text = {
            Column {
                GlassField("纬度 -90 ~ 90", latText) { latText = it }
                Spacer(Modifier.height(10.dp))
                GlassField("经度 -180 ~ 180", lonText) { lonText = it }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = Tok.Danger)
                }
            }
        },
        confirmButton = {
            Text(
                "确定",
                style = MaterialTheme.typography.labelLarge,
                color = Tok.Accent2,
                modifier = Modifier
                    .clickable {
                        val la = latText.trim().toDoubleOrNull()
                        val lo = lonText.trim().toDoubleOrNull()
                        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                            error = "格式不对，请检查数值范围"
                        } else {
                            onApply(la, lo)
                        }
                    }
                    .padding(12.dp)
            )
        },
        dismissButton = {
            Text(
                "取消",
                style = MaterialTheme.typography.labelLarge,
                color = Tok.Text3,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(12.dp)
            )
        }
    )
}

@Composable
fun GlassField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    mono: Boolean = true
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .glass(Tok.RInner)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodySmall, color = Tok.Text4)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = if (mono) Mono.copy(color = Tok.Text1)
            else MaterialTheme.typography.bodyLarge.copy(color = Tok.Text1),
            cursorBrush = SolidColor(Tok.Accent2),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
