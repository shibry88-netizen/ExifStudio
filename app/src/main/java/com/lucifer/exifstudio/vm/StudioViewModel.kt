package com.lucifer.exifstudio.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lucifer.exifstudio.data.Brand
import com.lucifer.exifstudio.data.Camera
import com.lucifer.exifstudio.data.Device
import com.lucifer.exifstudio.data.DeviceRepository
import com.lucifer.exifstudio.data.ExifEngine
import com.lucifer.exifstudio.data.ExifProfile
import com.lucifer.exifstudio.data.GeoClient
import com.lucifer.exifstudio.data.HistoryItem
import com.lucifer.exifstudio.data.HistoryStore
import com.lucifer.exifstudio.data.PhotoStore
import com.lucifer.exifstudio.data.Place
import com.lucifer.exifstudio.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudioState(
    val photoUri: Uri? = null,
    val profile: ExifProfile = ExifProfile(),
    /** 刚打开时的原始 EXIF，用来算「已改 N 项」 */
    val original: ExifProfile? = null,
    val brands: List<Brand> = emptyList(),
    val device: Device? = null,
    val camera: Camera? = null,
    val history: List<HistoryItem> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val savedUri: Uri? = null
) {
    val hasPhoto: Boolean get() = photoUri != null
    val deviceCount: Int get() = brands.sumOf { it.devices.size }
    val changedCount: Int get() = original?.let { diffCount(profile, it) } ?: 0
}

/** 只比可能被改写的字段，缩略图/宽高等不变的不算 */
private fun diffCount(a: ExifProfile, b: ExifProfile): Int {
    var n = 0
    if (a.make != b.make) n++
    if (a.model != b.model) n++
    if (a.software != b.software) n++
    if (a.lensModel != b.lensModel) n++
    if (a.fNumber != b.fNumber) n++
    if (a.exposureTime != b.exposureTime) n++
    if (a.iso != b.iso) n++
    if (a.focalLength != b.focalLength) n++
    if (a.focal35 != b.focal35) n++
    if (a.wbKelvin != b.wbKelvin || a.wbAuto != b.wbAuto) n++
    if (a.dateTime != b.dateTime) n++
    if (a.latitude != b.latitude || a.longitude != b.longitude) n++
    if (a.address != b.address) n++
    return n
}

class StudioViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val prefs = Prefs(app)
    private val historyStore = HistoryStore(app)

    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val brands = withContext(Dispatchers.IO) { DeviceRepository.brands(app) }
            val history = withContext(Dispatchers.IO) { historyStore.load() }
            _state.value = _state.value.copy(brands = brands, history = history)
        }
    }

    // ---------- 打开 / 读取 ----------

    fun openPhoto(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, savedUri = null)
            val profile = withContext(Dispatchers.IO) { ExifEngine.read(context, uri) }
            val normalized = if (profile.dateTime.isBlank()) {
                profile.copy(dateTime = ExifEngine.now())
            } else {
                profile
            }
            val match = DeviceRepository.match(_state.value.brands, normalized.model)
            _state.value = _state.value.copy(
                photoUri = uri,
                profile = normalized,
                original = normalized,
                device = match?.first,
                camera = match?.second,
                busy = false
            )
        }
    }

    /** 从「最近编辑」重新打开一张成品图 */
    fun openHistory(item: HistoryItem) {
        runCatching { Uri.parse(item.uri) }.getOrNull()?.let { openPhoto(it) }
    }

    // ---------- 编辑 ----------

    fun edit(transform: (ExifProfile) -> ExifProfile) {
        _state.value = _state.value.copy(profile = transform(_state.value.profile))
    }

    fun applyDevice(device: Device, camera: Camera) {
        _state.value = _state.value.copy(
            device = device,
            camera = camera,
            profile = _state.value.profile.applyDevice(device, camera)
        )
    }

    fun setLocation(lat: Double, lon: Double, address: String) {
        edit {
            it.copy(
                latitude = lat,
                longitude = lon,
                address = address,
                altitude = it.altitude ?: 0.0
            )
        }
    }

    fun clearLocation() {
        edit { it.copy(latitude = null, longitude = null, address = "", altitude = null) }
    }

    /** 撤销所有改动，回到刚打开时的状态 */
    fun reset() {
        val original = _state.value.original ?: return
        _state.value = _state.value.copy(profile = original)
    }

    fun setDateTime(value: String) = edit { it.copy(dateTime = value) }

    fun setIso(value: String) = edit { it.copy(iso = value.filter { c -> c.isDigit() }) }

    fun setWb(kelvin: Int, auto: Boolean) = edit { it.copy(wbKelvin = kelvin, wbAuto = auto) }

    fun setShutter(value: String) = edit { it.copy(exposureTime = value) }

    fun setFocal(value: String) = edit { it.copy(focalLength = value) }

    fun setFNumber(value: String) = edit { it.copy(fNumber = value) }

    // ---------- 保存 ----------

    fun save() {
        val snapshot = _state.value
        val uri = snapshot.photoUri ?: return
        viewModelScope.launch {
            _state.value = snapshot.copy(busy = true, message = null)
            val saved = withContext(Dispatchers.IO) {
                val file = PhotoStore.cacheFile(context, "out_${System.currentTimeMillis()}.jpg")
                val ok = ExifEngine.write(context, uri, snapshot.profile, file)
                if (ok) PhotoStore.saveImage(context, file, PhotoStore.displayName()) else null
            }
            val history = if (saved != null) {
                withContext(Dispatchers.IO) {
                    historyStore.add(
                        HistoryItem(
                            uri = saved.toString(),
                            label = snapshot.profile.describeDevice(),
                            time = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                _state.value.history
            }
            _state.value = _state.value.copy(
                busy = false,
                savedUri = saved,
                history = history,
                message = if (saved != null) {
                    "已写入并保存到相册 · Pictures/影迹"
                } else {
                    "写入失败。HEIC/ProRAW 等格式可能不支持原地写 EXIF，请换 JPEG 再试。"
                }
            )
        }
    }

    /** 一键去除全部元数据（含 GPS） */
    fun stripAll(onDone: (Boolean) -> Unit) {
        val snapshot = _state.value
        val uri = snapshot.photoUri
        if (uri == null) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            _state.value = snapshot.copy(busy = true)
            val saved = withContext(Dispatchers.IO) {
                val file = PhotoStore.cacheFile(context, "clean_${System.currentTimeMillis()}.jpg")
                val ok = ExifEngine.stripAll(context, uri, file)
                if (ok) PhotoStore.saveImage(context, file, PhotoStore.displayName("CLEAN")) else null
            }
            val history = if (saved != null) {
                withContext(Dispatchers.IO) {
                    historyStore.add(
                        HistoryItem(
                            uri = saved.toString(),
                            label = "已清除 EXIF",
                            time = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                _state.value.history
            }
            _state.value = _state.value.copy(
                busy = false,
                savedUri = saved,
                history = history,
                message = if (saved != null) "已生成无 EXIF 的干净副本" else "去元数据失败"
            )
            onDone(saved != null)
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ---------- 地图 / 搜索 ----------

    suspend fun searchPlaces(query: String): List<Place> = GeoClient(prefs.geoBaseUrl).search(query)

    suspend fun reverseGeocode(lat: Double, lon: Double): String =
        GeoClient(prefs.geoBaseUrl).reverse(lat, lon)

    // ---------- 地图源设置 ----------

    var tileKind: String
        get() = prefs.tileKind
        set(value) {
            prefs.tileKind = value
        }

    var customTileUrl: String
        get() = prefs.customTileUrl
        set(value) {
            prefs.customTileUrl = value
        }

    var geoBaseUrl: String
        get() = prefs.geoBaseUrl
        set(value) {
            prefs.geoBaseUrl = value
        }
}
