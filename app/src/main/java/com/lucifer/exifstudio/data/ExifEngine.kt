package com.lucifer.exifstudio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface as SysExif
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.log2

/**
 * EXIF 读写引擎。
 *
 * 读：整块解析成 [ExifProfile]，供界面展示与二次编辑。
 * 写：先把原图字节整体复制到目标文件（保留 ICC / 缩略图 / 其它标签），
 *     再用 ExifInterface 覆盖需要改的标签，避免重编码掉画质。
 *
 * ## 为什么必须补「相机骨架字段」
 *
 * AndroidX ExifInterface 只会写你显式 set 过的 tag，不会替你把一张图
 * 补成「合规的相机照片」。而 MIUI / HyperOS 相册的水印功能在读到
 * Exif SubIFD 却找不到 `ExifVersion` 时，会把整段判定为无效，
 * 直接显示「无拍摄信息」——哪怕 Make/Model/FNumber 一个不缺。
 *
 * 所以从 build-22 起，[applyProfile] 会补齐一整套相机骨架：
 * ExifVersion、FlashpixVersion、ColorSpace、ComponentsConfiguration、
 * ExposureProgram、MeteringMode、SceneType、ExposureMode、SensingMethod、
 * FileSource、OffsetTime*、APEX 派生值、FocalPlane 分辨率、LensSpecification。
 *
 * 注：AndroidX ExifInterface 至今（含 1.4.1）都没有写入缩略图的公开 API
 * （只有 getThumbnail 系列，setThumbnail 从未公开），因此改用 Exif 2.3 的
 * `FileSource = 3`（DSC，数码静态相机）作为等效的「相机来源」标记。
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
            val hasAltitude = ex.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null
            val altitude = if (hasAltitude) {
                runCatching { ex.getAltitude(0.0) }.getOrDefault(0.0)
            } else {
                null
            }
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
                altitude = altitude,
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
        return try {
            dst.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(src) ?: return false
            input.use { ins ->
                dst.outputStream().use { out -> ins.copyTo(out, 128 * 1024) }
            }

            // 源图不是 JPEG（截屏、微信/QQ 转发图常是 PNG / WebP）时先转码。
            // 否则 ExifInterface 会按 .jpg 扩展名当 JPEG 解析，直接失败。
            if (!isJpeg(dst) && !transcodeToJpeg(dst)) return false

            val ex = ExifInterface(dst.absolutePath)
            applyProfile(ex, profile)
            ex.saveAttributes()
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun isJpeg(file: File): Boolean = runCatching {
        file.inputStream().use { ins ->
            val b = ByteArray(3)
            ins.read(b) == 3 &&
                b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()
        }
    }.getOrDefault(false)

    private fun transcodeToJpeg(file: File): Boolean = runCatching {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching false
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        file.delete()
        if (!tmp.renameTo(file)) {
            tmp.delete()
            false
        } else {
            true
        }
    }.getOrDefault(false)

    /**
     * 把 [p] 的全部字段写到 [ex]。
     *
     * 分两档：
     *  - 核心字段（Make/Model/曝光三要素/时间/GPS）直接写，失败即整体失败；
     *  - 骨架与锦上添花的字段用 [safe] 包裹，个别写不进去也不影响整体落盘。
     */
    private fun applyProfile(ex: ExifInterface, p: ExifProfile) {
        // ===== 1. 身份 =====
        if (p.make.isNotBlank()) ex.setAttribute(ExifInterface.TAG_MAKE, p.make.trim())
        if (p.model.isNotBlank()) ex.setAttribute(ExifInterface.TAG_MODEL, p.model.trim())
        if (p.software.isNotBlank()) ex.setAttribute(ExifInterface.TAG_SOFTWARE, p.software.trim())

        // Orientation 只在原图没有时才补 1。
        // 否则竖拍照片（Orientation=6/8）会被强行掰横。
        if (ex.getAttribute(ExifInterface.TAG_ORIENTATION).isNullOrBlank()) {
            safe { ex.setAttribute(ExifInterface.TAG_ORIENTATION, "1") }
        }

        // ===== 2. Exif 骨架（MIUI 判定「是否拍摄照片」的关键）=====
        safe { ex.setAttribute(ExifInterface.TAG_EXIF_VERSION, "0232") }
        safe { ex.setAttribute(ExifInterface.TAG_FLASHPIX_VERSION, "0100") }
        safe { ex.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1") }
        safe { ex.setAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION, "1,2,3,0") }
        safe { ex.setAttribute(ExifInterface.TAG_INTEROPERABILITY_INDEX, "R98") }
        // FileSource = 3 表示 DSC（数码静态相机），替代无法写入的内嵌缩略图
        safe { ex.setAttribute(ExifInterface.TAG_FILE_SOURCE, "3") }

        // ===== 3. 曝光三要素 + 派生 APEX 值 =====
        if (p.fNumber.isNotBlank()) {
            ex.setAttribute(ExifInterface.TAG_F_NUMBER, p.fNumber)
            val f = p.fNumber.toDoubleOrNull()
            if (f != null && f > 0) {
                // ApertureValue = 2 * log2(N)；定焦手机镜头最大值即当前值
                val av = rational(2.0 * log2(f))
                safe { ex.setAttribute(ExifInterface.TAG_APERTURE_VALUE, av) }
                safe { ex.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, av) }
            }
        }
        if (p.exposureTime.isNotBlank()) {
            ex.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, p.exposureTime.trim())
            exposureSeconds(p.exposureTime)?.let { sec ->
                if (sec > 0) {
                    // ShutterSpeedValue = -log2(t)
                    safe { ex.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, rational(-log2(sec))) }
                }
            }
        }
        if (p.iso.isNotBlank()) {
            ex.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, p.iso)
            safe { ex.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, p.iso) }
            safe { ex.setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, "1") }
        }

        // ===== 4. 焦距 =====
        if (p.focalLength.isNotBlank()) {
            ex.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, p.focalLength)
        }
        if (p.focal35.isNotBlank()) {
            safe { ex.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, p.focal35) }
        }
        safe { ex.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "1") }

        // ===== 5. 相机行为指纹 =====
        // 水印模板把「光圈 · ISO · 快门」当一个整体渲染，
        // 缺曝光程序/测光模式时部分机型会整体降级成「无拍摄信息」。
        safe { ex.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, "2") }   // 普通程序
        safe { ex.setAttribute(ExifInterface.TAG_METERING_MODE, "5") }      // 图案测光
        safe { ex.setAttribute(ExifInterface.TAG_SCENE_TYPE, "1") }         // 直接拍摄
        safe { ex.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, "0") }      // 自动曝光
        safe { ex.setAttribute(ExifInterface.TAG_SENSING_METHOD, "2") }     // 单芯片彩色面阵
        safe { ex.setAttribute(ExifInterface.TAG_FLASH, "16") }             // 未闪光（强制关）
        safe { ex.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, "0") } // 标准
        safe { ex.setAttribute(ExifInterface.TAG_CUSTOM_RENDERED, "0") }    // 普通处理
        safe { ex.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, "0") }
        safe { ex.setAttribute(ExifInterface.TAG_GAIN_CONTROL, "0") }
        safe { ex.setAttribute(ExifInterface.TAG_CONTRAST, "0") }
        safe { ex.setAttribute(ExifInterface.TAG_SATURATION, "0") }
        safe { ex.setAttribute(ExifInterface.TAG_SHARPNESS, "0") }

        // ===== 6. 镜头 =====
        if (p.lensModel.isNotBlank()) {
            safe { ex.setAttribute(ExifInterface.TAG_LENS_MODEL, p.lensModel.trim()) }
            if (p.make.isNotBlank()) {
                safe { ex.setAttribute(ExifInterface.TAG_LENS_MAKE, p.make.trim()) }
            }
            if (p.focalLength.isNotBlank() && p.fNumber.isNotBlank()) {
                // LensSpecification: 最短焦距, 最短光圈, 最长焦距, 最大光圈
                safe {
                    ex.setAttribute(
                        ExifInterface.TAG_LENS_SPECIFICATION,
                        "${p.focalLength},${p.fNumber},${p.focalLength},${p.fNumber}"
                    )
                }
            }
        }

        // ===== 7. 尺寸 =====
        if (p.width > 0 && p.height > 0) {
            safe { ex.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, p.width.toString()) }
            safe { ex.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, p.height.toString()) }
        }
        safe { ex.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, "72") }
        safe { ex.setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, "72") }
        safe { ex.setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, "2") } // 英寸

        // ===== 8. 白平衡 =====
        // WhiteBalance 0=自动 1=手动；LightSource 记录场景光源枚举
        safe {
            ex.setAttribute(ExifInterface.TAG_WHITE_BALANCE, if (p.wbAuto) "0" else "1")
            lightSourceFor(p.wbKelvin)?.let {
                ex.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, it.toString())
            }
        }

        // ===== 9. 时间 + 时区偏移 =====
        val stamp = p.dateTime.ifBlank { formatDate(Date()) }
        ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        ex.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        ex.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
        val off = tzOffset()
        safe { ex.setAttribute(ExifInterface.TAG_OFFSET_TIME, off) }
        safe { ex.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, off) }
        safe { ex.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, off) }

        // ===== 10. GPS =====
        if (p.hasLocation) {
            val lat = p.latitude!!
            val lon = p.longitude!!
            safe { ex.setAttribute(ExifInterface.TAG_GPS_VERSION_ID, "2.3.0.0") }
            ex.setLatLong(lat, lon)
            val alt = p.altitude ?: 0.0
            // 海拔在 EXIF 里存绝对值，正负交给 AltitudeRef
            safe { ex.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, abs(alt).toString()) }
            safe { ex.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt < 0) "1" else "0") }
            safe { ex.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "NETWORK") }
            safe { ex.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateStamp()) }
            safe { ex.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeStamp()) }
            if (p.address.isNotBlank()) {
                safe { ex.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, p.address) }
            }
        }
    }

    // ---------- 校验 ----------

    /**
     * 双引擎回读校验。
     *
     * 只用 AndroidX 回读是不够的——它读得到，不代表系统自带的
     * `android.media.ExifInterface`（MIUI 相册底层用的就是它）也读得到。
     * 这里同时用两套解析器读一遍，把差异暴露给用户。
     */
    fun verify(file: File): String {
        var axModel = false
        var axLens = false
        var axIso = false
        var axGps = false
        runCatching {
            val e = ExifInterface(file.absolutePath)
            axModel = e.getAttribute(ExifInterface.TAG_MODEL).orEmpty().isNotBlank()
            axLens = e.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty().isNotBlank()
            axIso = (
                e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: e.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                ).orEmpty().isNotBlank()
            axGps = e.latLong != null
        }

        val sys = runCatching {
            val e = SysExif(file.absolutePath)
            listOf(
                e.getAttribute(SysExif.TAG_MODEL).orEmpty().isNotBlank(),
                e.getAttribute(SysExif.TAG_EXPOSURE_TIME).orEmpty().isNotBlank(),
                e.getAttribute(SysExif.TAG_F_NUMBER).orEmpty().isNotBlank(),
                e.getAttribute(SysExif.TAG_ISO_SPEED_RATINGS).orEmpty().isNotBlank(),
                e.getAttribute(SysExif.TAG_DATETIME_ORIGINAL).orEmpty().isNotBlank()
            )
        }.getOrNull()

        val sysText = if (sys == null) {
            "系统引擎✗解析"
        } else {
            "系统 型号${tick(sys[0])}快门${tick(sys[1])}光圈${tick(sys[2])}ISO${tick(sys[3])}时间${tick(sys[4])}"
        }

        return "AX 型号${tick(axModel)}镜头${tick(axLens)}ISO${tick(axIso)}定位${tick(axGps)}\n$sysText"
    }

    private fun tick(v: Boolean): String = if (v) "✓" else "✗"

    // ---------- 清除 ----------

    /** 彻底去掉元数据（含 GPS）：重编码一份干净副本 */
    fun stripAll(context: Context, src: Uri, dst: File): Boolean {
        return try {
            dst.parentFile?.mkdirs()
            val bitmap = context.contentResolver.openInputStream(src)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return false
            dst.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            bitmap.recycle()
            true
        } catch (t: Throwable) {
            false
        }
    }

    // ---------- 工具 ----------

    private inline fun safe(block: () -> Unit) {
        runCatching(block)
    }

    /** EXIF RATIONAL 用小数字符串表示，AndroidX 会转成 分子/分母 */
    private fun rational(v: Double): String = String.format(Locale.US, "%.4f", v)

    /** "1/120" / "0.5" / "2" → 秒 */
    private fun exposureSeconds(raw: String): Double? = runCatching {
        val t = raw.trim()
        if (t.contains('/')) {
            val parts = t.split('/')
            parts[0].trim().toDouble() / parts[1].trim().toDouble()
        } else {
            t.toDouble()
        }
    }.getOrNull()

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

    fun formatDate(date: Date): String =
        SimpleDateFormat(DATE_PATTERN, Locale.US).format(date)

    fun now(): String = formatDate(Date())

    /** "yyyy:MM:dd HH:mm:ss" → 毫秒时间戳，用于 MediaStore.DATE_TAKEN */
    fun parseStamp(value: String): Long? = runCatching {
        SimpleDateFormat(DATE_PATTERN, Locale.US).parse(value.trim())?.time
    }.getOrNull()

    /** 当前时区偏移，形如 +08:00 */
    private fun tzOffset(): String {
        val totalMinutes = TimeZone.getDefault()
            .getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absMin = abs(totalMinutes)
        return String.format(Locale.US, "%s%02d:%02d", sign, absMin / 60, absMin % 60)
    }

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
