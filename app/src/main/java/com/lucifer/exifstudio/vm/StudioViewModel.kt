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
    val brands: List<Brand> = emptyList(),
    val device: Device? = null,
    val camera: Camera? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val savedUri: Uri? = null
) {
    val hasPhoto: Boolean get() = photoUri != null
    val deviceCount: Int get() = brands.sumOf { it.devices.size }
}

class StudioViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val brands = withContext(Dispatchers.IO) { DeviceRepository.brands(app) }
            _state.value = _state.value.copy(brands = brands)
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
                device = match?.first,
                camera = match?.second,
                busy = false
            )
        }
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
            _state.value = _state.value.copy(
                busy = false,
                savedUri = saved,
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
            _state.value = _state.value.copy(
                busy = false,
                savedUri = saved,
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
