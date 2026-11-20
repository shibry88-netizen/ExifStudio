package com.lucifer.exifstudio.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoStore {

    /**
     * 写入相册 Pictures/影迹，无需存储权限（分区存储 + IS_PENDING）。
     *
     * 两个细节很重要：
     *  1. 把 EXIF 里的拍摄时间同步写进 MediaStore.DATE_TAKEN，
     *     否则相册按「导入时间」排序，水印模板也可能取不到时间；
     *  2. 落盘后再显式触发一次 MediaScanner——MIUI 有自己的媒体库索引，
     *     光靠 IS_PENDING 的 update 未必能让它重建 EXIF 缓存。
     */
    fun saveImage(
        context: Context,
        file: File,
        displayName: String,
        dateTaken: Long? = null
    ): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/影迹")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            if (dateTaken != null && dateTaken > 0) {
                put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
            }
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out, 128 * 1024) }
        }
        val done = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
            if (dateTaken != null && dateTaken > 0) {
                put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
            }
        }
        context.contentResolver.update(uri, done, null, null)

        // 显式重扫，逼相册（尤其 MIUI / HyperOS）重建该文件的 EXIF 索引
        runCatching {
            val path = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (!path.isNullOrBlank()) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("image/jpeg"),
                    null
                )
            }
        }

        uri
    }.getOrNull()

    fun cacheFile(context: Context, name: String = "working.jpg"): File {
        val dir = File(context.cacheDir, "edit")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }

    fun displayName(prefix: String = "EXIF"): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "${prefix}_${sdf.format(Date())}.jpg"
    }
}
