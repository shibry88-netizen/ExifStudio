package com.lucifer.exifstudio

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃兜底：把堆栈写进
 *   Android/data/com.lucifer.exifstudio/files/crash.log
 * 方便定位“点了没反应/闪退”这种不好复现的问题。
 */
class ExifStudioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter()
                throwable.printStackTrace(PrintWriter(stack))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val dir = getExternalFilesDir(null) ?: filesDir
                File(dir, "crash.log")
                    .appendText("\n===== $stamp =====\n$stack\n")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
