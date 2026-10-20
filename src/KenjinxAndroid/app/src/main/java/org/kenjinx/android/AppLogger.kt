package org.kenjinx.android

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {
    private val executor = Executors.newSingleThreadExecutor()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val dir = File(context.getExternalFilesDir(null), "Logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, "kenjinx_debug.log")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeLog("FATAL", "KotlinCrash", "Thread: ${thread.name}\n${Log.getStackTraceString(throwable)}")
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
    }

    fun writeLog(level: String, tag: String, msg: String) {
        Log.println(when(level) { "ERROR", "FATAL" -> Log.ERROR; "WARN" -> Log.WARN; else -> Log.INFO }, tag, msg)
        executor.execute {
            try {
                val time = dateFormat.format(Date())
                val line = "$time [$level] [$tag] $msg\n"
                FileWriter(logFile, true).use { it.append(line) }
            } catch (_: Exception) {}
        }
    }
}