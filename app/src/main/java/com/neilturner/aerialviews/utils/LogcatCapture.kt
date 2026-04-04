package com.neilturner.aerialviews.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatCapture {
    private var process: Process? = null
    private var logFile: File? = null

    fun start(context: Context) {
        stop() // Ensure any existing process is stopped

        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }

        // Clear existing log files in the cache logs directory
        logsDir.listFiles()?.forEach { it.delete() }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val filename = "AerialViews_log_$timestamp.txt"
        logFile = File(logsDir, filename)

        try {
            val command = arrayOf("logcat", "-v", "threadtime")
            process = Runtime.getRuntime().exec(command)
            
            // Use a separate thread to read from logcat output and write to file
            Thread {
                try {
                    process?.inputStream?.use { input ->
                        FileOutputStream(logFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: IOException) {
                    Timber.e(e, "Error writing logcat to file")
                }
            }.start()

            Timber.i("Started logcat capture to ${logFile?.absolutePath}")
        } catch (e: IOException) {
            Timber.e(e, "Failed to start logcat process")
        }
    }

    fun stop() {
        try {
            process?.destroy()
            process = null
            Timber.i("Stopped logcat capture")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping logcat process")
        }
    }

    fun saveToDocuments(context: Context): File? {
        val currentLogFile = logFile ?: return null
        if (!currentLogFile.exists()) return null

        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!docsDir.exists()) {
                docsDir.mkdirs()
            }

            val destFile = File(docsDir, currentLogFile.name)
            currentLogFile.copyTo(destFile, overwrite = true)

            // Notify Media Scanner so the file appears immediately over MTP / USB
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                null,
                null
            )
            
            Timber.i("Saved logcat copy to ${destFile.absolutePath}")
            return destFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to save log to Documents")
            return null
        }
    }
}
