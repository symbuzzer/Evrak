package com.avalibeyaz.evrak.ui

import android.content.Context
import com.avalibeyaz.evrak.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

suspend fun exportLogcatAndOpen(
    context: Context,
    viewModel: MainViewModel,
    onDismiss: () -> Unit = {},
    errorTimeMs: Long = System.currentTimeMillis()
) {
    withContext(Dispatchers.IO) {
        val nowMs = errorTimeMs
        val cutoffMs = nowMs - 60_000L

        val pid = android.os.Process.myPid().toString()
        val lines = ArrayList<String>()

        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid", pid))
            val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val cal = Calendar.getInstance()
            val currentYear = cal[Calendar.YEAR]
            var inWindow = false

            process.inputStream.bufferedReader().useLines { seq ->
                seq.forEach { line ->
                    if (line.length >= 18) {
                        val datePart = line.substring(0, 18)
                        try {
                            val parsedDate = sdf.parse(datePart)
                            if (parsedDate != null) {
                                cal.time = parsedDate
                                cal[Calendar.YEAR] = currentYear
                                val lineMs = cal.timeInMillis
                                inWindow = (lineMs >= cutoffMs)
                            }
                        } catch (_: Exception) {}
                    }
                    if (inWindow) {
                        lines.add(line)
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback if filtering resulted in empty list
        if (lines.isEmpty()) {
            try {
                val fallbackProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid", pid))
                fallbackProcess.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { lines.add(it) }
                }
                fallbackProcess.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val content = if (lines.isNotEmpty()) lines.joinToString("\n") else "Logcat kaydı bulunamadı."

        val dateFormat = SimpleDateFormat("yy-MM-dd-HH-mm-ss", Locale.US)
        val fileName = "logcat-${dateFormat.format(Date(nowMs))}.evraklog"

        val cacheDir = File(context.filesDir, "evrak_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val file = File(cacheDir, "${System.currentTimeMillis()}_$fileName")
        file.writeText(content)

        withContext(Dispatchers.Main) {
            viewModel.ensureEvrak(file.absolutePath, fileName) { evrak ->
                openDocumentTask(context, evrak.path, evrak.name)
                onDismiss()
            }
        }
    }
}
