package com.lucifer.exifstudio.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条编辑记录：成品图 URI + 机型 + 时间 */
data class HistoryItem(
    val uri: String,
    val label: String,
    val time: Long
)

/** 最近编辑记录，最多保留 12 条 */
class HistoryStore(context: Context) {

    private val sp = context.getSharedPreferences("exif_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryItem> {
        val raw = sp.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri")
                if (uri.isBlank()) null else HistoryItem(
                    uri = uri,
                    label = o.optString("label"),
                    time = o.optLong("time")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(item: HistoryItem): List<HistoryItem> {
        val merged = (listOf(item) + load().filter { it.uri != item.uri }).take(MAX)
        runCatching {
            val array = JSONArray()
            merged.forEach { h ->
                array.put(
                    JSONObject()
                        .put("uri", h.uri)
                        .put("label", h.label)
                        .put("time", h.time)
                )
            }
            sp.edit().putString(KEY, array.toString()).apply()
        }
        return merged
    }

    fun clear(): List<HistoryItem> {
        sp.edit().remove(KEY).apply()
        return emptyList()
    }

    private companion object {
        const val KEY = "items"
        const val MAX = 12
    }
}
