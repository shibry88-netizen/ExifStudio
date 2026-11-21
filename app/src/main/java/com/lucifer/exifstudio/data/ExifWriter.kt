package com.lucifer.exifstudio.data

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 规范 JPEG/Exif APP1 写入器。
 *
 * ## 为什么不继续用 ExifInterface.setAttribute
 *
 * androidx.exifinterface:1.3.7 的 setAttribute 有两条会把标签静默丢掉的路径：
 *
 *   1. guessDataFormat 猜出的格式与标签声明的 IFD_FORMAT 不匹配时 `continue`。
 *      URATIONAL 类标签（FocalLength / ApertureValue / ShutterSpeedValue）只要给的是
 *      小数字符串就必然落进这条分支；只有 FNumber / ExposureTime / DigitalZoomRatio /
 *      SubjectDistance 因为在 RATIONAL_TAGS_HANDLED_AS_DECIMALS_FOR_COMPATIBILITY
 *      集合里，才会被提前转成 "n/d" 逃过一劫。
 *   2. UNDEFINED / BYTE 类标签统一走 `createUndefined(value.getBytes(ASCII))`，
 *      于是 ComponentsConfiguration 被写成 8 个 ASCII 字节 "1,2,3,0"，
 *      GPSVersionID 被写成 7 个 ASCII 字节 "2.3.0.0"，SceneType / FileSource 写成
 *      '1' / '3' 而不是 0x01 / 0x03。
 *
 * 严格解析器（系统 android.media.ExifInterface、MIUI/HyperOS 相册水印模块、
 * exiftool -validate）看到这些类型错乱的标签后，会把整段 Exif SubIFD 判成无效：
 * 表现就是快门一栏打叉、相册水印显示「无拍摄信息」。
 *
 * 本类直接序列化 TIFF 结构，所有标签按声明类型写字面字节，不经过任何格式猜测。
 */
object ExifWriter {

    // ---- TIFF 字段类型 ----
    private const val T_BYTE = 1
    private const val T_ASCII = 2
    private const val T_SHORT = 3
    private const val T_LONG = 4
    private const val T_RATIONAL = 5
    private const val T_UNDEFINED = 7
    private const val T_SRATIONAL = 10

    private const val TAG_EXIF_IFD_POINTER = 0x8769
    private const val TAG_GPS_IFD_POINTER = 0x8825
    private const val TAG_INTEROP_IFD_POINTER = 0xA005

    private const val DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"

    /** APP1 段内容上限：段长字段 2 字节，所以负载最多 65535-2 */
    private const val APP1_MAX = 65533

    /** 最近一次失败原因，供界面/日志显示，不参与逻辑 */
    @Volatile
    var lastError: String = ""
        private set

    private class Entry(
        val tag: Int,
        val type: Int,
        val value: ByteArray
    ) {
        val count: Int get() = when (type) {
            T_ASCII, T_BYTE, T_UNDEFINED -> value.size
            T_SHORT -> value.size / 2
            T_LONG -> value.size / 4
            T_RATIONAL, T_SRATIONAL -> value.size / 8   // 每项是 8 字节的 n/d
            else -> value.size
        }

        /** 指针标签先占位，排完布局再回填真实偏移 */
        fun patch(bytes: ByteArray) {
            bytes.copyInto(value, 0, 0, minOf(bytes.size, value.size))
        }
    }

    // ---------- 入口 ----------

    /**
     * 把 [p] 写成规范的 APP1 段替换进 [file]。
     *
     * [keepOrientation] 是原图的 Orientation；缺失 / 0 / 非法时落 1。
     * 返回 false 时 [lastError] 有原因。
     */
    fun apply(file: File, p: ExifProfile, width: Int, height: Int, keepOrientation: Int): Boolean {
        lastError = ""
        return runCatching {
            if (!file.isFile || file.length() < 4) {
                lastError = "目标文件不存在"
                return@runCatching false
            }
            val (w, h) = sizeOf(file, width, height)
            if (w <= 0 || h <= 0) {
                lastError = "读不到图片尺寸"
                return@runCatching false
            }

            val tiff = buildTiff(p, w, h, keepOrientation)
            if (tiff.size + 6 > APP1_MAX) {
                lastError = "地址过长，APP1 段写不下"
                return@runCatching false
            }

            val payload = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
            val rebuilt = spliceApp1(file.readBytes(), payload)
            if (rebuilt == null) {
                lastError = "JPEG 段结构解析失败"
                return@runCatching false
            }
            file.writeBytes(rebuilt)
            true
        }.getOrElse {
            lastError = it.message ?: it.javaClass.simpleName
            false
        }
    }

    private fun sizeOf(file: File, width: Int, height: Int): Pair<Int, Int> {
        if (width > 0 && height > 0) return width to height
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }
        return opts.outWidth to opts.outHeight
    }

    // ---------- TIFF 组装 ----------

    private fun buildTiff(p: ExifProfile, w: Int, h: Int, keepOrientation: Int): ByteArray {
        val stamp = safeStamp(p.dateTime)
        val offset = tzOffset()

        // ===== IFD0 =====
        val ifd0 = mutableListOf<Entry>()
        ascii(p.make.trim())?.let { ifd0 += Entry(0x010F, T_ASCII, it) }
        ascii(p.model.trim())?.let { ifd0 += Entry(0x0110, T_ASCII, it) }
        ifd0 += Entry(0x0112, T_SHORT, ushort(orientation(keepOrientation)))
        ifd0 += Entry(0x011A, T_RATIONAL, rational(72.0))
        ifd0 += Entry(0x011B, T_RATIONAL, rational(72.0))
        ifd0 += Entry(0x0128, T_SHORT, ushort(2))          // inch
        ascii(p.software.trim())?.let { ifd0 += Entry(0x0131, T_ASCII, it) }
        ifd0 += Entry(0x0132, T_ASCII, asciiRaw(stamp))
        ifd0 += Entry(0x0213, T_SHORT, ushort(1))          // YCbCrPositioning = centered
        ifd0 += Entry(TAG_EXIF_IFD_POINTER, T_LONG, ulong(0))
        if (p.hasLocation) ifd0 += Entry(TAG_GPS_IFD_POINTER, T_LONG, ulong(0))

        // ===== Exif SubIFD =====
        val exif = mutableListOf<Entry>()
        exposureSeconds(p.exposureTime)?.let { sec ->
            exif += Entry(0x829A, T_RATIONAL, exposureRational(sec))
            exif += Entry(0x9201, T_SRATIONAL, srational(-log2(sec)))
        }
        p.fNumber.toDoubleOrNull()?.takeIf { it > 0 }?.let { f ->
            val fn = f.toFloat().toDouble()
            exif += Entry(0x829D, T_RATIONAL, rational(fn))
            val av = 2.0 * log2(fn)
            exif += Entry(0x9202, T_RATIONAL, rational(av))   // ApertureValue
            exif += Entry(0x9205, T_RATIONAL, rational(av))   // MaxApertureValue
        }
        exif += Entry(0x8822, T_SHORT, ushort(2))          // 程序曝光
        p.iso.filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }?.let {
            exif += Entry(0x8827, T_SHORT, ushort(it))    // ISOSpeedRatings
            exif += Entry(0x8830, T_SHORT, ushort(1))     // SensitivityType = SOS
        }
        exif += Entry(0x9000, T_UNDEFINED, "0232".toByteArray(Charsets.US_ASCII))
        exif += Entry(0x9003, T_ASCII, asciiRaw(stamp))   // DateTimeOriginal
        exif += Entry(0x9004, T_ASCII, asciiRaw(stamp))   // DateTimeDigitized
        exif += Entry(0x9010, T_ASCII, asciiRaw(offset))
        exif += Entry(0x9011, T_ASCII, asciiRaw(offset))
        exif += Entry(0x9012, T_ASCII, asciiRaw(offset))
        exif += Entry(0x9101, T_UNDEFINED, byteArrayOf(1, 2, 3, 0))  // YCbCr
        exif += Entry(0x9204, T_SRATIONAL, srational(0.0))           // ExposureBiasValue
        exif += Entry(0x9207, T_SHORT, ushort(5))          // 图案测光
        lightSourceFor(p.wbKelvin)?.let { exif += Entry(0x9208, T_SHORT, ushort(it)) }
        exif += Entry(0x9209, T_SHORT, ushort(16))         // 未闪光（强制关）
        p.focalLength.toDoubleOrNull()?.takeIf { it > 0 }?.let {
            exif += Entry(0x920A, T_RATIONAL, rational(it))
        }
        if (p.iso.isNotBlank()) {
            exif += Entry(0x9286, T_UNDEFINED, userComment(p))
        }
        exif += Entry(0xA000, T_UNDEFINED, "0100".toByteArray(Charsets.US_ASCII))
        exif += Entry(0xA001, T_SHORT, ushort(1))          // sRGB
        exif += Entry(0xA002, T_LONG, ulong(w))
        exif += Entry(0xA003, T_LONG, ulong(h))
        exif += Entry(0xA20E, T_RATIONAL, rational(72.0))
        exif += Entry(0xA20F, T_RATIONAL, rational(72.0))
        exif += Entry(0xA210, T_SHORT, ushort(2))
        exif += Entry(0xA217, T_SHORT, ushort(2))          // 单芯片彩色面阵
        exif += Entry(0xA401, T_SHORT, ushort(0))          // CustomRendered = 普通
        exif += Entry(0xA402, T_SHORT, ushort(0))          // 自动曝光
        exif += Entry(0xA403, T_SHORT, ushort(if (p.wbAuto) 0 else 1))
        exif += Entry(0xA404, T_RATIONAL, rational(1.0))   // DigitalZoomRatio
        p.focal35.toIntOrNull()?.takeIf { it > 0 }?.let {
            exif += Entry(0xA405, T_SHORT, ushort(it))     // FocalLengthIn35mmFilm
        }
        exif += Entry(0xA406, T_SHORT, ushort(0))          // SceneCaptureType
        exif += Entry(0xA407, T_SHORT, ushort(0))          // GainControl
        exif += Entry(0xA408, T_SHORT, ushort(0))          // Contrast
        exif += Entry(0xA409, T_SHORT, ushort(0))          // Saturation
        exif += Entry(0xA40A, T_SHORT, ushort(0))          // Sharpness
        exif += Entry(0xA40C, T_SHORT, ushort(0))          // SubjectDistanceRange
        // SubjectArea：1 个点 + 宽高（4 个 SHORT），部分水印模板读它定位
        exif += Entry(0x9214, T_SHORT, ushort(w / 2) + ushort(h / 2) + ushort(w) + ushort(h))
        val fl = p.focalLength.toDoubleOrNull()
        val fn2 = p.fNumber.toDoubleOrNull()
        if (fl != null && fl > 0 && fn2 != null && fn2 > 0) {
            exif += Entry(
                0xA432, T_RATIONAL,
                rational(fl) + rational(fn2) + rational(fl) + rational(fn2)
            )
        }
        ascii(p.make.trim())?.let { exif += Entry(0xA433, T_ASCII, it) }
        ascii(p.lensModel.trim())?.let { exif += Entry(0xA434, T_ASCII, it) }
        exif += Entry(0xA300, T_UNDEFINED, byteArrayOf(3)) // FileSource = DSC
        exif += Entry(TAG_INTEROP_IFD_POINTER, T_LONG, ulong(0))

        // ===== Interop IFD =====
        val interop = mutableListOf<Entry>()
        interop += Entry(0x0001, T_ASCII, asciiRaw("R98"))
        interop += Entry(0x0002, T_UNDEFINED, "0100".toByteArray(Charsets.US_ASCII))

        // ===== GPS IFD =====
        val gps = mutableListOf<Entry>()
        if (p.hasLocation) {
            val lat = p.latitude!!
            val lon = p.longitude!!
            gps += Entry(0x0000, T_BYTE, byteArrayOf(2, 3, 0, 0))   // GPSVersionID
            gps += Entry(0x0001, T_ASCII, asciiRaw(if (lat < 0) "S" else "N"))
            gps += Entry(0x0002, T_RATIONAL, dms(abs(lat)))
            gps += Entry(0x0003, T_ASCII, asciiRaw(if (lon < 0) "W" else "E"))
            gps += Entry(0x0004, T_RATIONAL, dms(abs(lon)))
            val alt = p.altitude ?: 0.0
            gps += Entry(0x0005, T_BYTE, byteArrayOf(if (alt < 0) 1 else 0))
            gps += Entry(0x0006, T_RATIONAL, rational(abs(alt)))
            gps += Entry(0x0007, T_RATIONAL, gpsClock())
            // GPSProcessingMethod：规范只认 GPS / CELLID / WLAN / MANUAL 四个值，
            // 此 App 走网络定位，用 CELLID（"NETWORK" 会被 exiftool 判 invalid）
            gps += Entry(0x001B, T_UNDEFINED, prefixed("CELLID"))
            if (p.address.isNotBlank()) {
                gps += Entry(0x001C, T_UNDEFINED, unicodeBlock(p.address))
            }
            gps += Entry(0x001D, T_ASCII, asciiRaw(gpsDate()))
        }

        return serialize(ifd0, exif, interop, gps)
    }

    /** 布局：header + IFD0 + ExifIFD + InteropIFD + GPSIFD + 数据区 */
    private fun serialize(
        ifd0: MutableList<Entry>,
        exif: MutableList<Entry>,
        interop: MutableList<Entry>,
        gps: MutableList<Entry>
    ): ByteArray {
        ifd0.sortBy { it.tag }
        exif.sortBy { it.tag }
        interop.sortBy { it.tag }
        gps.sortBy { it.tag }

        val size0 = 2 + 12 * ifd0.size + 4
        val sizeE = 2 + 12 * exif.size + 4
        val sizeI = if (interop.isEmpty()) 0 else 2 + 12 * interop.size + 4
        val sizeG = if (gps.isEmpty()) 0 else 2 + 12 * gps.size + 4

        val exifOffset = 8 + size0
        val interopOffset = exifOffset + sizeE
        val gpsOffset = interopOffset + sizeI

        ifd0.first { it.tag == TAG_EXIF_IFD_POINTER }.patch(ulong(exifOffset))
        if (interop.isNotEmpty()) {
            exif.first { it.tag == TAG_INTEROP_IFD_POINTER }.patch(ulong(interopOffset))
        } else {
            exif.removeAll { it.tag == TAG_INTEROP_IFD_POINTER }
        }
        if (gps.isNotEmpty()) {
            ifd0.first { it.tag == TAG_GPS_IFD_POINTER }.patch(ulong(gpsOffset))
        }

        // sizeI / sizeG 在移除 Interop 指针后可能变化，这里按最终列表重算
        val finalE = 2 + 12 * exif.size + 4
        val finalG = if (gps.isEmpty()) 0 else 2 + 12 * gps.size + 4
        if (finalE != sizeE) {
            // exif 少了一项：ExifIFD 起始不变，但 Interop / GPS 偏移要整体前移
            val shift = sizeE - finalE
            if (interop.isNotEmpty()) {
                exif.first { it.tag == TAG_INTEROP_IFD_POINTER }.patch(ulong(interopOffset - shift))
            }
            if (gps.isNotEmpty()) {
                ifd0.first { it.tag == TAG_GPS_IFD_POINTER }.patch(ulong(gpsOffset - shift))
            }
        }

        val data = ByteArrayOutputStream()
        var cursor = 8 + size0 + finalE + sizeI + finalG

        fun emit(buf: ByteArrayOutputStream, list: List<Entry>) {
            buf.writeU16(list.size)
            for (e in list) {
                buf.writeU16(e.tag)
                buf.writeU16(e.type)
                buf.writeU32(e.count)
                if (e.value.size <= 4) {
                    val cell = ByteArray(4)
                    e.value.copyInto(cell)
                    buf.write(cell)
                } else {
                    buf.writeU32(cursor)
                    data.write(e.value)
                    if (e.value.size % 2 == 1) data.write(0)
                    cursor += e.value.size + (e.value.size % 2)
                }
            }
            buf.writeU32(0)
        }

        val body = ByteArrayOutputStream()
        emit(body, ifd0)
        emit(body, exif)
        if (interop.isNotEmpty()) emit(body, interop)
        if (gps.isNotEmpty()) emit(body, gps)

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x49, 0x49, 0x2A, 0x00))   // "II" + 42
        out.writeU32(8)
        out.write(body.toByteArray())
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    // ---------- JPEG 段替换 ----------

    /**
     * 删掉所有 Exif APP1，插入新的；结构非法返回 null。
     *
     * 位置讲究：JFIF 规范要求 APP0 紧跟 SOI，所以新 APP1 排在 APP0 之后，
     * 而不是直接顶在 SOI 上——直接顶上去会让部分严格解码器把 JFIF 判成二次写入。
     *
     * SOS 之后的熵编码数据不是「段」，遇到 SOS 就停止解析、整体搬运剩余字节。
     * 之前这里一直解析到文件尾，碰到熵数据里第一个非 0xFF 字节就 return null，
     * 于是任何真实照片都会拼接失败——这是 ExifWriter 一直没生效的直接原因。
     */
    private fun spliceApp1(jpeg: ByteArray, payload: ByteArray): ByteArray? {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null

        // [start, markerPos, end, marker]
        val segments = ArrayList<IntArray>(32)
        var i = 2
        var scanEnd = -1
        var hasEoi = false

        while (i < jpeg.size) {
            if (jpeg[i] != 0xFF.toByte()) return null
            var marker = i + 1
            while (marker < jpeg.size && jpeg[marker] == 0xFF.toByte()) marker++
            if (marker >= jpeg.size) return null
            val m = jpeg[marker].toInt() and 0xFF

            if (m == 0xD8 || m == 0x01 || (m in 0xD0..0xD7)) {
                segments += intArrayOf(i, marker, marker + 1, m)
                i = marker + 1
                continue
            }
            if (m == 0xD9) {
                scanEnd = marker + 1
                hasEoi = true
                break
            }
            if (marker + 2 >= jpeg.size) return null
            val len = ((jpeg[marker + 1].toInt() and 0xFF) shl 8) or
                (jpeg[marker + 2].toInt() and 0xFF)
            val end = marker + 1 + len
            if (len < 2 || end > jpeg.size) return null
            segments += intArrayOf(i, marker, end, m)
            i = end
            if (m == 0xDA) {
                scanEnd = end
                break
            }
        }
        if (scanEnd < 0) return null

        val out = ByteArrayOutputStream(jpeg.size + payload.size)
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))

        var written = false
        for (s in segments) {
            val start = s[0]
            val markerPos = s[1]
            val end = s[2]
            val m = s[3]

            val isExif = m == 0xE1 && end - markerPos >= 8 &&
                String(jpeg, markerPos + 3, 4, Charsets.ISO_8859_1) == "Exif"

            if (isExif) {
                if (!written) {
                    writeApp1(out, payload)
                    written = true
                }
                continue
            }

            // 遇到第一个非 APP0 段之前，先把新 APP1 插进去
            if (!written && m != 0xE0) {
                writeApp1(out, payload)
                written = true
            }

            if (m == 0xE0 && isJfif(jpeg, markerPos, end)) {
                writeNormalizedJfif(out, jpeg, start, markerPos, end)
            } else {
                out.write(jpeg, start, end - start)
            }
        }

        if (!written) writeApp1(out, payload)

        if (hasEoi) {
            out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
            if (scanEnd < jpeg.size) out.write(jpeg, scanEnd, jpeg.size - scanEnd)
        } else {
            // scanEnd 指向 SOS 头之后；尾部含熵编码数据与 EOI，原样搬运
            out.write(jpeg, scanEnd, jpeg.size - scanEnd)
        }
        return out.toByteArray()
    }

    private fun writeApp1(out: ByteArrayOutputStream, payload: ByteArray) {
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        out.writeBeU16(payload.size + 2)
        out.write(payload)
    }

    /** APP0 段是否为 JFIF（"JFIF\u0000"），且长度够到分辨率的 5 个字节 */
    private fun isJfif(jpeg: ByteArray, markerPos: Int, end: Int): Boolean =
        end - markerPos >= 16 &&
            String(jpeg, markerPos + 3, 4, Charsets.ISO_8859_1) == "JFIF"

    /**
     * 复制 APP0，把 units 置 1、X/Y 密度置 72（与 IFD0 的 ResolutionUnit=inch 对齐）。
     *
     * JFIF 段内部字节位（相对段起点 0xFF）：
     *   0=FF 1=E0 2..3=长度 4..8="JFIF\u0000" 9=主版本 10=次版本
     *   11=单位 12..13=X密度 14..15=Y密度
     * 所以按 markerPos 算的偏移是 +10 / +11..12 / +13..14。
     */
    private fun writeNormalizedJfif(
        out: ByteArrayOutputStream,
        jpeg: ByteArray,
        start: Int,
        markerPos: Int,
        end: Int
    ) {
        val seg = jpeg.copyOfRange(start, end)
        val base = markerPos - start
        seg[base + 10] = 1                                    // units = 每英寸点数
        seg[base + 11] = 0; seg[base + 12] = 72               // Xdensity = 72
        seg[base + 13] = 0; seg[base + 14] = 72               // Ydensity = 72
        out.write(seg)
    }

    // ---------- 取值 ----------

    private fun orientation(v: Int): Int = if (v in 1..8) v else 1

    private fun safeStamp(raw: String): String {
        val t = raw.trim()
        if (t.length == 19 &&
            runCatching { SimpleDateFormat(DATE_PATTERN, Locale.US).parse(t) }.getOrNull() != null
        ) {
            return t
        }
        return SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())
    }

    private fun exposureSeconds(raw: String): Double? = runCatching {
        val t = raw.trim()
        if (t.contains('/')) {
            val s = t.split('/')
            s[0].trim().toDouble() / s[1].trim().toDouble()
        } else {
            t.toDouble()
        }
    }.getOrNull()?.takeIf { it > 0 }

    /**
     * 快门优先写成 1/N 的相机习惯写法。
     * 若按通用 double→分数 转换，1/120 会变成 83/9960，水印上就显示成怪数字。
     */
    private fun exposureRational(sec: Double): ByteArray {
        if (sec >= 1.0) return rational(sec)
        val d = (1.0 / sec).roundToInt().coerceIn(1, 1_000_000)
        return ByteArray(8).also {
            writeInt(it, 0, 1)
            writeInt(it, 4, d)
        }
    }

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

    /** 十进制度 → 度/分/秒 三个 RATIONAL */
    private fun dms(deg: Double): ByteArray {
        val d = deg.toInt()
        val rest = (deg - d) * 60.0
        val m = rest.toInt()
        val s = (rest - m) * 60.0
        return rational(d.toDouble()) + rational(m.toDouble()) + rational(s)
    }

    private fun gpsClock(): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return rational(cal.get(Calendar.HOUR_OF_DAY).toDouble()) +
            rational(cal.get(Calendar.MINUTE).toDouble()) +
            rational(cal.get(Calendar.SECOND).toDouble())
    }

    /** 必须与 [gpsClock] 同为 UTC，否则 exiftool 报 GPS 日期与时间戳不一致 */
    private fun gpsDate(): String =
        SimpleDateFormat("yyyy:MM:dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    /**
     * UserComment 优先用 ASCII 头；含非 ASCII 字符时改走 UNICODE 块。
     * 原来固定 prefixed + " · " 分隔符，US_ASCII 会把中间点编成 '?'，水印上就成了乱码。
     */
    private fun userComment(p: ExifProfile): ByteArray {
        val text = buildString {
            append("ExifStudio")
            if (p.model.isNotBlank()) append(" | ").append(p.model.trim())
        }
        return if (text.all { it.code in 0x20..0x7E }) prefixed(text) else unicodeBlock(text)
    }

    /** UNDEFINED 文本块："ASCII\u0000\u0000\u0000" + 正文 */
    private fun prefixed(text: String): ByteArray =
        byteArrayOf(0x41, 0x53, 0x43, 0x49, 0x49, 0, 0, 0) + text.toByteArray(Charsets.US_ASCII)

    /**
     * UNDEFINED 文本块："UNICODE\u0000" + UTF-16。
     * 字节序必须跟随 TIFF header —— 本文件是 II（小端），所以用 UTF-16LE，
     * 否则 exiftool 报 "Wrong byte order for ... UNICODE text"。
     */
    private fun unicodeBlock(text: String): ByteArray =
        byteArrayOf(0x55, 0x4E, 0x49, 0x43, 0x4F, 0x44, 0x45, 0) +
            text.toByteArray(Charsets.UTF_16LE)

    private fun tzOffset(): String {
        val total = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (total >= 0) "+" else "-"
        val a = abs(total)
        return String.format(Locale.US, "%s%02d:%02d", sign, a / 60, a % 60)
    }

    // ---------- 编码 ----------

    private fun ascii(s: String): ByteArray? = if (s.isBlank()) null else asciiRaw(s)

    /** ASCII 值必须以 0 结尾，count 含这个 0 */
    private fun asciiRaw(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII) + 0

    private fun ushort(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun ulong(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    private fun rational(v: Double): ByteArray {
        val (n, d) = toFraction(v)
        return ByteArray(8).also {
            writeInt(it, 0, n)
            writeInt(it, 4, d)
        }
    }

    private fun srational(v: Double): ByteArray = rational(v)

    /** 数值 → 最简分数，分母放大到 10000 后约分 */
    private fun toFraction(v: Double): Pair<Int, Int> {
        if (v.isNaN() || v.isInfinite()) return 0 to 1
        val scaled = (v * 10000.0).roundToLong()
        if (scaled == 0L) return 0 to 1
        var n = scaled
        var d = 10000L
        var a = abs(n)
        var b = d
        while (b != 0L) {
            val t = a % b
            a = b
            b = t
        }
        val g = if (a == 0L) 1L else a
        n /= g
        d /= g
        while (n > Int.MAX_VALUE || d > Int.MAX_VALUE) {
            n /= 2
            d /= 2
            if (d == 0L) return 0 to 1
        }
        return n.toInt() to d.toInt()
    }

    private fun writeInt(buf: ByteArray, at: Int, v: Int) {
        buf[at] = (v and 0xFF).toByte()
        buf[at + 1] = ((v shr 8) and 0xFF).toByte()
        buf[at + 2] = ((v shr 16) and 0xFF).toByte()
        buf[at + 3] = ((v shr 24) and 0xFF).toByte()
    }

    /** TIFF 内部值：小端（II） */
    private fun ByteArrayOutputStream.writeU16(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }

    /** JPEG 段长：大端。和 TIFF 小端值不能共用同一个函数 */
    private fun ByteArrayOutputStream.writeBeU16(v: Int) {
        write((v shr 8) and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }
}
