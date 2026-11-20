package com.lucifer.exifstudio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系转换。
 * 相机 EXIF 里的 GPS 必须是 WGS-84（国际标准），
 * 而国内地图瓦片（高德/腾讯等）是 GCJ-02 火星坐标，
 * 两者直接混用会有 300~600 米偏移，所以进出地图都要转换。
 */
object Coord {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun wgs84ToGcj02(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)
        return doubleArrayOf(lat + dLat, lon + dLon)
    }

    fun gcj02ToWgs84(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        var wgsLat = lat
        var wgsLon = lon
        repeat(4) {
            val g = wgs84ToGcj02(wgsLat, wgsLon)
            wgsLat += lat - g[0]
            wgsLon += lon - g[1]
        }
        return doubleArrayOf(wgsLat, wgsLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}

/**
 * 地名检索 / 逆地理编码。
 * 默认走 OpenStreetMap Nominatim：免密钥、支持中文、可自建。
 * 想换高德/百度/自建 Photon，只要把 baseUrl 改成同结构的服务即可。
 */
class GeoClient(baseUrl: String = PREF_DEFAULT) {

    private val root = baseUrl.trimEnd('/')

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "$root/search?format=jsonv2&limit=10&addressdetails=1&accept-language=zh-CN,zh,en&q=" +
            URLEncoder.encode(query, "UTF-8")
        val body = httpGet(url) ?: return@withContext emptyList()
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val display = o.optString("display_name")
            val name = o.optString("name").ifBlank { display.substringBefore("，").substringBefore(",") }
            Place(title = name, subtitle = display, lat = lat, lon = lon)
        }
    }

    suspend fun reverse(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val url = "$root/reverse?format=jsonv2&zoom=18&accept-language=zh-CN,zh,en&lat=$lat&lon=$lon"
        val body = httpGet(url) ?: return@withContext ""
        runCatching { JSONObject(body).optString("display_name") }.getOrDefault("")
    }

    private fun httpGet(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ExifStudio/1.0 (Android)")
        conn.setRequestProperty("Accept", "application/json")
        conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    companion object {
        const val PREF_DEFAULT = "https://nominatim.openstreetmap.org"
    }
}

/** 地图瓦片源：高德(GCJ-02) / OSM(WGS-84) / 自建 XYZ 服务 */
object MapTiles {

    const val GAODE = "gaode"
    const val OSM = "osm"
    const val CUSTOM = "custom"

    fun source(kind: String, customTemplate: String): ITileSource = when {
        kind == GAODE -> GaodeTileSource()
        kind == CUSTOM && customTemplate.isNotBlank() -> XyzTileSource(customTemplate)
        else -> TileSourceFactory.MAPNIK
    }

    /** GCJ-02 瓦片需要把 WGS-84 坐标先转过去才能对齐 */
    fun usesGcj(kind: String): Boolean = kind == GAODE

    fun toMap(kind: String, lat: Double, lon: Double): DoubleArray =
        if (usesGcj(kind)) Coord.wgs84ToGcj02(lat, lon) else doubleArrayOf(lat, lon)

    fun toWgs(kind: String, lat: Double, lon: Double): DoubleArray =
        if (usesGcj(kind)) Coord.gcj02ToWgs84(lat, lon) else doubleArrayOf(lat, lon)

    fun label(kind: String): String = when (kind) {
        GAODE -> "高德"
        OSM -> "OSM"
        CUSTOM -> "自建"
        else -> "OSM"
    }
}

private class GaodeTileSource : OnlineTileSourceBase(
    "Gaode", 1, 19, 256, "", arrayOf("https://webrd01.is.autonavi.com/appmaptile")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val server = 1 + ((x + y) % 4)
        return "https://webrd0$server.is.autonavi.com/appmaptile" +
            "?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$z"
    }

    override fun getCopyrightNotice(): String = "© 高德地图"
}

private class XyzTileSource(private val template: String) : OnlineTileSourceBase(
    "Custom", 1, 19, 256, "", arrayOf("")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return template
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }

    override fun getCopyrightNotice(): String = "自定义瓦片源"
}

/** 轻量偏好存储 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("exif_studio", Context.MODE_PRIVATE)

    var tileKind: String
        get() = sp.getString("tile_kind", MapTiles.GAODE) ?: MapTiles.GAODE
        set(value) {
            sp.edit().putString("tile_kind", value).apply()
        }

    var customTileUrl: String
        get() = sp.getString("tile_url", "") ?: ""
        set(value) {
            sp.edit().putString("tile_url", value).apply()
        }

    var geoBaseUrl: String
        get() = sp.getString("geo_url", GeoClient.PREF_DEFAULT) ?: GeoClient.PREF_DEFAULT
        set(value) {
            sp.edit().putString("geo_url", value).apply()
        }
}
