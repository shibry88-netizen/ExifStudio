package com.lucifer.exifstudio.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * EXIF 读写引擎。
 *
 * 读：整块解析成 [ExifProfile]，供界面展示与二次编辑。
 * 写：先把原图字节整体复制到目标文件（保留 ICC / 缩略图 / 其它标签），
 *     再用 ExifInterface 覆盖需要改的标签，避免重编码掉画质。
 */
object ExifEngine {

    private const val DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"

    // ---------- 读取 ----------

    fun read(context: Context, uri: Uri): ExifProfile {
        val base = runCatching { readExif(context, uri) }.getOrNull() ?: ExifProfile()
        val (w, h) = dimensions(context, uri)
        return base.copy(width = w, height = h)
    }

    private fun readExif(context: Context, uri: Uri): ExifProfile? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use {
            val ex = ExifInterface(it)
            val latLong = ex.latLong
            val altitude = ex.getAltitude()
            ExifProfile(
                make = ex.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
                model = ex.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
                software = ex.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty(),
                lensModel = ex.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty(),
                fNumber = ex.getAttribute(ExifInterface.TAG_F_NUMBER).orEmpty(),
                exposureTime = ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME).orEmpty(),
                iso = ex.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: ex.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS).orEmpty(),
                focalLength = ex.getAttribute(ExifInterface.TAG_FOCAL_LENGTH).orEmpty(),
                focal35 = ex.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM).orEmpty(),
                wbAuto = ex.getAttribute(ExifInterface.TAG_WHITE_BALANCE) != "1",
                dateTime = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).orEmpty(),
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
                altitude = if (altitude.isNaN()) null else altitude,
                address = ex.getAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION).orEmpty()
            )
        }
    }

    private fun dimensions(context: Context, uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    // ---------- 写入 ----------

    /** 把 [src] 复制成 [dst] 并覆盖 EXIF；返回是否成功落盘 */
    fun write(context: Context, src: Uri, profile: ExifProfile, dst: File): Boolean {
        return runCatching {
            dst.parentFile?.mkdirs()
            context.contentResolver.openInputStream(src)?.use { input ->
                dst.outputStream().use { out -> input.copyTo(out, 128 * 1024) }
            } ?: return false

            val ex = ExifInterface(dst.absolutePath)
            apply(ex, profile)
            ex.saveAttributes()
        }.getOrDefault(false)
    }

    private fun apply(ex: ExifInterface, p: ExifProfile) {
        if (p.make.isNotBlank()) ex.setAttribute(ExifInterface.TAG_MAKE, p.make)
        if (p.model.isNotBlank()) ex.setAttribute(ExifInterface.TAG_MODEL, p.model)
        if (p.software.isNotBlank()) ex.setAttribute(ExifInterface.TAG_SOFTWARE, p.software)
        if (p.lensModel.isNotBlank()) ex.setAttribute(ExifInterface.TAG_LENS_MODEL, p.lensModel)
        if (p.fNumber.isNotBlank()) ex.setAttribute(ExifInterface.TAG_F_NUMBER, p.fNumber)
        if (p.exposureTime.isNotBlank()) ex.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, p.exposureTime)
        if (p.iso.isNotBlank()) {
            ex.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, p.iso)
            ex.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, p.iso)
        }
        if (p.focalLength.isNotBlank()) ex.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, p.focalLength)
        if (p.focal35.isNotBlank()) {
            ex.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, p.focal35)
            ex.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "1")
        }

        // 白平衡：EXIF WhiteBalance 0=自动 1=手动，LightSource 记录场景光源
        ex.setAttribute(ExifInterface.TAG_WHITE_BALANCE, if (p.wbAuto) "0" else "1")
        lightSourceFor(p.wbKelvin)?.let {
            ex.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, it.toString())
        }

        val stamp = p.dateTime.ifBlank { formatDate(Date()) }
        ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        ex.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        ex.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)

        if (p.hasLocation) {
            val lat = p.latitude!!
            val lon = p.longitude!!
            ex.setLatLong(lat, lon)
            val alt = p.altitude ?: 0.0
            ex.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, alt)
            ex.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt < 0) "1" else "0")
            ex.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "NETWORK")
            ex.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateStamp())
            ex.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeStamp())
            if (p.address.isNotBlank()) {
                ex.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, p.address)
            }
        }
    }

    /** 色温 → EXIF LightSource 枚举值 */
    private fun lightSourceFor(kelvin: Int): Int? = when {
        kelvin <= 0 -> null
        kelvin < 3500 -> 3
        kelvin < 4800 -> 1
        kelvin < 5300 -> 23
        kelvin < 5800 -> 20
        kelvin < 6300 -> 21
        kelvin < 7000 -> 10
        else -> 9
    }

    // ---------- 清除 ----------

    /** 彻底去掉元数据（含 GPS）：重编码一份干净副本 */
    fun stripAll(context: Context, src: Uri, dst: File): Boolean = runCatching {
        dst.parentFile?.mkdirs()
        val bitmap = context.contentResolver.openInputStream(src)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return false
        dst.outputStream().use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
        }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    // ---------- 工具 ----------

    fun formatDate(date: Date): String =
        SimpleDateFormat(DATE_PATTERN, Locale.US).format(date)

    fun now(): String = formatDate(Date())

    private fun gpsDateStamp(): String =
        SimpleDateFormat("yyyy:MM:dd", Locale.US).format(Date())

    private fun gpsTimeStamp(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val seconds = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 +
            cal.get(Calendar.SECOND)
        return seconds.toString()
    }
}
