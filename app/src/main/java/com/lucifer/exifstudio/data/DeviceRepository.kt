package com.lucifer.exifstudio.data

import android.content.Context
import org.json.JSONObject

/**
 * 机型库。数据来自 assets/devices.json，schema 为紧凑数组：
 * [facing, role, lensModel, focal35, fNumber, sensor, iso, wb, shutter]
 */
object DeviceRepository {

    @Volatile
    private var cache: List<Brand>? = null

    fun brands(context: Context): List<Brand> {
        cache?.let { return it }
        val parsed = runCatching { parse(context) }.getOrElse { emptyList() }
        cache = parsed
        return parsed
    }

    private fun parse(context: Context): List<Brand> {
        val text = context.assets.open("devices.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val brandArray = root.getJSONArray("brands")
        val out = ArrayList<Brand>(brandArray.length())

        for (i in 0 until brandArray.length()) {
            val bj = brandArray.getJSONObject(i)
            val brandId = bj.getString("id")
            val brandName = bj.getString("name")
            val zh = bj.optString("zh", brandName)
            val deviceArray = bj.getJSONArray("devices")
            val devices = ArrayList<Device>(deviceArray.length())

            for (j in 0 until deviceArray.length()) {
                val dj = deviceArray.getJSONObject(j)
                val cams = dj.getJSONArray("cam")
                val cameras = ArrayList<Camera>(cams.length())
                for (k in 0 until cams.length()) {
                    val c = cams.getJSONArray(k)
                    cameras += Camera(
                        facing = c.getString(0),
                        role = c.getString(1),
                        lensModel = c.getString(2),
                        focal35 = c.getInt(3),
                        fNumber = c.getDouble(4),
                        sensor = c.getString(5),
                        iso = c.getInt(6),
                        wb = c.getString(7),
                        shutter = c.getString(8)
                    )
                }
                devices += Device(
                    id = dj.getString("id"),
                    brandId = brandId,
                    brandName = brandName,
                    name = dj.getString("n"),
                    year = dj.getInt("y"),
                    make = dj.getString("make"),
                    model = dj.getString("model"),
                    software = dj.optString("sw", ""),
                    cameras = cameras
                )
            }
            out += Brand(id = brandId, name = brandName, zh = zh, devices = devices)
        }
        return out
    }

    /** 按 EXIF 里读到的 Model 字段反查机型，用于「识别这张照片的相机」 */
    fun match(brands: List<Brand>, model: String): Pair<Device, Camera>? {
        if (model.isBlank()) return null
        val target = model.trim().lowercase()
        for (brand in brands) {
            for (device in brand.devices) {
                val candidates = listOf(device.model, device.name, device.id)
                if (candidates.any { it.lowercase() == target || target.contains(it.lowercase()) }) {
                    val cam = device.rear.firstOrNull() ?: device.cameras.firstOrNull()
                    if (cam != null) return device to cam
                }
            }
        }
        return null
    }
}
