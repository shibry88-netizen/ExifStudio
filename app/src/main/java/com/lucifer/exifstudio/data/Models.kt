package com.lucifer.exifstudio.data

import kotlin.math.round

/** 单个镜头。facing: R=后置 / F=前置 */
data class Camera(
    val facing: String,
    val role: String,
    val lensModel: String,
    val focal35: Int,
    val fNumber: Double,
    val sensor: String,
    val iso: Int,
    val wb: String,
    val shutter: String
) {
    val isFront: Boolean get() = facing == "F"

    /**
     * 由等效焦距反推物理焦距。真实 EXIF 里 FocalLength 写的是物理焦距，
     * FocalLengthIn35mmFilm 写等效值，两者必须自洽，否则一眼假。
     */
    val focalActual: Double
        get() {
            val crop = when {
                role.contains("超广角") -> 6.7
                role.contains("前置") -> 6.0
                else -> 3.5
            }
            return round(focal35 / crop * 100.0) / 100.0
        }

    val summary: String get() = "${focal35}mm · f/$fNumber"
}

data class Device(
    val id: String,
    val brandId: String,
    val brandName: String,
    val name: String,
    val year: Int,
    val make: String,
    val model: String,
    val software: String,
    val cameras: List<Camera>
) {
    val rear: List<Camera> get() = cameras.filter { !it.isFront }
    val front: List<Camera> get() = cameras.filter { it.isFront }
    val lensCount: Int get() = cameras.size
}

data class Brand(
    val id: String,
    val name: String,
    val zh: String,
    val devices: List<Device>
)

/** 可编辑的 EXIF 快照。空字符串表示「不写入该标签」。 */
data class ExifProfile(
    val make: String = "",
    val model: String = "",
    val software: String = "",
    val lensModel: String = "",
    val fNumber: String = "",
    val exposureTime: String = "",
    val iso: String = "",
    val focalLength: String = "",
    val focal35: String = "",
    val wbKelvin: Int = 0,
    val wbAuto: Boolean = true,
    val dateTime: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val address: String = "",
    val width: Int = 0,
    val height: Int = 0
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    fun applyDevice(device: Device, camera: Camera): ExifProfile = copy(
        make = device.make,
        model = device.model,
        software = device.software,
        lensModel = camera.lensModel,
        fNumber = formatF(camera.fNumber),
        exposureTime = camera.shutter,
        iso = camera.iso.toString(),
        focalLength = camera.focalActual.toString(),
        focal35 = camera.focal35.toString(),
        wbAuto = camera.wb == "auto",
        wbKelvin = camera.wb.toIntOrNull() ?: 0
    )

    fun describeDevice(): String =
        if (model.isBlank()) "未设置" else model

    fun describeLens(): String {
        if (lensModel.isBlank()) return "未设置"
        val f = if (fNumber.isBlank()) "" else " · f/$fNumber"
        val fl = if (focal35.isBlank()) "" else " · ${focal35}mm"
        return "$lensModel$fl$f"
    }

    fun describeWb(): String = when {
        wbAuto && wbKelvin == 0 -> "自动"
        wbAuto -> "自动 · ${wbKelvin}K"
        wbKelvin > 0 -> "${wbKelvin}K 手动"
        else -> "未设置"
    }

    companion object {
        fun formatF(v: Double): String {
            val s = String.format("%.2f", v)
            return if (s.endsWith("0")) s.dropLast(1) else s
        }
    }
}

/** 地图搜索结果，经纬度一律 WGS-84 */
data class Place(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double
)
