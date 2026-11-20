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

/** 检索结果：要么有列表，要么带一句人话的错误 */
sealed class GeoResult {
    data class Ok(val places: List<Place>) : GeoResult()
    data class Fail(val message: String) : GeoResult()
}

object GeoProvider {
    const val AUTO = "auto"
    const val OSM = "osm"
    const val PHOTON = "photon"
    const val AMAP = "amap"
}

/**
 * 地名检索 / 逆地理编码。
 *
 * 国内网络下 Nominatim 经常超时或被限流，所以做了多源兜底：
 *   高德（需自备 Key，国内最准） → OSM Nominatim → Photon。
 * 任何一个源拿到结果就直接返回；全部失败才报错，错误会在界面上显示出来，不再静默。
 */
class GeoClient(
    private val provider: String = GeoProvider.AUTO,
    private val amapKey: String = "",
    baseUrl: String = NOMINATIM
) {

    private val root = baseUrl.trimEnd('/')

    suspend fun search(query: String): GeoResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext GeoResult.Ok(emptyList())

        val order = when (provider) {
            GeoProvider.OSM -> listOf(GeoProvider.OSM, GeoProvider.PHOTON)
            GeoProvider.PHOTON -> listOf(GeoProvider.PHOTON, GeoProvider.OSM)
            GeoProvider.AMAP -> listOf(GeoProvider.AMAP)
            else -> if (amapKey.isNotBlank()) {
                listOf(GeoProvider.AMAP, GeoProvider.OSM, GeoProvider.PHOTON)
            } else {
                listOf(GeoProvider.OSM, GeoProvider.PHOTON)
            }
        }

        var lastError = ""
        var sawEmpty = false

        for (p in order) {
            val places: List<Place>? = when (p) {
                GeoProvider.AMAP -> {
                    if (amapKey.isBlank()) {
                        lastError = "尚未配置高德 Key"
                        null
                    } else {
                        runCatching { searchAmap(query) }
                            .onFailure { lastError = it.message ?: "高德检索失败" }
                            .getOrNull()
                    }
                }

                GeoProvider.PHOTON -> runCatching { searchPhoton(query) }
                    .onFailure { lastError = it.message ?: "Photon 检索失败" }
                    .getOrNull()

                else -> runCatching { searchNominatim(query) }
                    .onFailure { lastError = it.message ?: "OSM 检索失败" }
                    .getOrNull()
            }

            if (places != null && places.isNotEmpty()) return@withContext GeoResult.Ok(places)
            if (places != null) sawEmpty = true
        }

        if (sawEmpty) GeoResult.Ok(emptyList())
        else GeoResult.Fail(lastError.ifBlank { "无法连接地名服务，请检查网络" })
    }

    suspend fun reverse(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        if (amapKey.isNotBlank()) {
            runCatching { reverseAmap(lat, lon) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return@withContext it }
        }
        runCatching { reverseNominatim(lat, lon) }.getOrNull().orEmpty()
    }

    // ---------- 各源实现 ----------

    private fun searchNominatim(q: String): List<Place> {
        val url = "$root/search?format=jsonv2&limit=10&addressdetails=1&accept-language=zh-CN,zh,en&q=" +
            URLEncoder.encode(q, "UTF-8")
        val body = httpGet(url) ?: error("OSM 无响应（可能被限流或网络不通）")
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val display = o.optString("display_name")
            val name = o.optString("name").ifBlank {
                display.substringBefore("，").substringBefore(",")
            }
            Place(title = name, subtitle = display, lat = lat, lon = lon)
        }
    }

    private fun searchPhoton(q: String): List<Place> {
        val url = "https://photon.komoot.io/api/?limit=10&lang=zh&q=" +
            URLEncoder.encode(q, "UTF-8")
        val body = httpGet(url) ?: error("Photon 无响应")
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.optJSONObject(i) ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: return@mapNotNull null
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return@mapNotNull null
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            val sub = listOf(
                p.optString("city"), p.optString("state"), p.optString("country")
            ).filter { it.isNotBlank() }.joinToString(" · ")
            val name = p.optString("name").ifBlank { p.optString("street").ifBlank { sub } }
            Place(title = name, subtitle = sub, lat = lat, lon = lon)
        }
    }

    private fun searchAmap(q: String): List<Place> {
        val url = "https://restapi.amap.com/v3/place/text?key=$amapKey&offset=10&page=1&extensions=base&keywords=" +
            URLEncoder.encode(q, "UTF-8")
        val body = httpGet(url) ?: error("高德无响应")
        val o = JSONObject(body)
        if (o.optString("status") != "1") {
            error("高德：" + o.optString("info").ifBlank { "返回异常" })
        }
        val pois = o.optJSONArray("pois") ?: return emptyList()
        return (0 until pois.length()).mapNotNull { i ->
            val poi = pois.optJSONObject(i) ?: return@mapNotNull null
            val parts = poi.optString("location").split(",")
            if (parts.size != 2) return@mapNotNull null
            val gLon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val gLat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            // 高德返回 GCJ-02，统一转成 WGS-84 存起来
            val wgs = Coord.gcj02ToWgs84(gLat, gLon)
            val sub = listOf(
                poi.optString("cityname"), poi.optString("adname"), poi.optString("address")
            ).filter { it.isNotBlank() && it != "[]" }.joinToString(" · ")
            Place(
                title = poi.optString("name").ifBlank { sub },
                subtitle = sub,
                lat = wgs[0],
                lon = wgs[1]
            )
        }
    }

    private fun reverseNominatim(lat: Double, lon: Double): String {
        val url = "$root/reverse?format=jsonv2&zoom=18&accept-language=zh-CN,zh,en&lat=$lat&lon=$lon"
        val body = httpGet(url) ?: return ""
        return runCatching { JSONObject(body).optString("display_name") }.getOrDefault("")
    }

    private fun reverseAmap(lat: Double, lon: Double): String {
        val g = Coord.wgs84ToGcj02(lat, lon)
        val url = "https://restapi.amap.com/v3/geocode/regeo?key=$amapKey&extensions=base&location=" +
            "${g[1]},${g[0]}"
        val body = httpGet(url) ?: return ""
        val o = JSONObject(body)
        if (o.optString("status") != "1") return ""
        return o.optJSONObject("regeocode")?.optString("formatted_address").orEmpty()
    }

    private fun httpGet(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ExifStudio/1.2 (Android)")
        conn.setRequestProperty("Accept", "application/json")
        conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    companion object {
        const val NOMINATIM = "https://nominatim.openstreetmap.org"
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
        get() = sp.getString("geo_url", GeoClient.NOMINATIM) ?: GeoClient.NOMINATIM
        set(value) {
            sp.edit().putString("geo_url", value).apply()
        }

    var geoProvider: String
        get() = sp.getString("geo_provider", GeoProvider.AUTO) ?: GeoProvider.AUTO
        set(value) {
            sp.edit().putString("geo_provider", value).apply()
        }

    /** 高德 Web 服务 Key，填了之后检索/逆编码会优先走高德（国内最准） */
    var amapKey: String
        get() = sp.getString("amap_key", "") ?: ""
        set(value) {
            sp.edit().putString("amap_key", value).apply()
        }
}
