package com.avalibeyaz.evrak.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.avalibeyaz.evrak.R
import java.io.File

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun getMimeType(path: String): String {
    return when {
        path.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        path.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        path.endsWith(".doc", ignoreCase = true) -> "application/msword"
        path.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        path.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
        path.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        path.endsWith(".ppt", ignoreCase = true) -> "application/vnd.ms-powerpoint"
        path.endsWith(".tif", ignoreCase = true) || path.endsWith(".tiff", ignoreCase = true) -> "image/tiff"
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".gif", true) -> "image/gif"
        path.endsWith(".webp", true) -> "image/webp"
        path.endsWith(".bmp", true) -> "image/bmp"
        path.endsWith(".heic", true) -> "image/heic"
        path.endsWith(".avif", true) -> "image/avif"
        path.endsWith(".udf", true) -> "application/x-udf"
        path.endsWith(".html", true) || path.endsWith(".htm", true) -> "text/html"
        path.endsWith(".txt", true) -> "text/plain"
        path.endsWith(".evraklog", true) -> "text/plain"
        path.endsWith(".zip", true) -> "application/zip"
        path.endsWith(".eyp", true) -> "application/eyp"
        else -> "application/octet-stream"
    }
}

fun isArchiveType(path: String): Boolean {
    return path.endsWith(".zip", ignoreCase = true) || path.endsWith(".eyp", ignoreCase = true)
}

fun Intent?.isExternalOpenIntent(): Boolean {
    if (this == null) return false
    if (getBooleanExtra("from_open_with", false)) return true
    if (getBooleanExtra("internal_open", false)) return false
    return action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND
}

fun Context.showOpenedWithEvrakToast() {
    Toast.makeText(applicationContext, getString(R.string.opened_with_evrak), Toast.LENGTH_SHORT).show()
}

fun openDocumentTask(context: Context, filePath: String, displayName: String) {
    if (isArchiveType(filePath)) {
        val intent = Intent(context, com.avalibeyaz.evrak.MainActivity::class.java).apply {
            putExtra("file_path", filePath)
            putExtra("display_name", displayName)
            putExtra("open_archive", true)
            putExtra("internal_open", true)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    } else {
        val file = File(filePath)
        val uri = Uri.fromFile(file)
        val intent = Intent(context, com.avalibeyaz.evrak.DocumentActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, getMimeType(filePath))
            putExtra("file_path", filePath)
            putExtra("display_name", displayName)
            putExtra("internal_open", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        context.startActivity(intent)
    }
}

fun shareEvrakLogEmail(context: Context, filePath: String, displayName: String? = null) {
    val file = File(filePath)
    if (!file.exists()) return
    val subject = displayName ?: file.name.let {
        if (it.contains("_") && it.substringBefore("_").all { c -> c.isDigit() }) it.substringAfter("_") else it
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val emailIntent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf("alipolatbeyaz@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        val packageManager = context.packageManager
        val resolveInfos = packageManager.queryIntentActivities(emailIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)

        val targetedIntents = mutableListOf<Intent>()
        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName.lowercase()
            val cls = resolveInfo.activityInfo.name.lowercase()

            val isEmailApp = pkg.contains("mail") ||
                    pkg.contains("outlook") ||
                    pkg.contains("k9") ||
                    pkg.contains("thunderbird") ||
                    pkg.contains("spark") ||
                    pkg.contains("bluemail") ||
                    pkg.contains("aqua") ||
                    cls.contains("mail") ||
                    cls.contains("compose")

            val isExcluded = pkg.contains("file") ||
                    pkg.contains("manager") ||
                    pkg.contains("pdf") ||
                    pkg.contains("reader") ||
                    pkg.contains("viewer") ||
                    pkg.contains("office") ||
                    pkg.contains("doc") ||
                    pkg.contains("drive") ||
                    pkg.contains("bluetooth") ||
                    pkg.contains("whatsapp") ||
                    pkg.contains("telegram") ||
                    pkg.contains("messaging") ||
                    pkg.contains("mms") ||
                    pkg.contains("sms") ||
                    pkg.contains("browser") ||
                    pkg.contains("chrome")

            if (isEmailApp && !isExcluded) {
                val targetedIntent = Intent(emailIntent).setPackage(resolveInfo.activityInfo.packageName)
                targetedIntents.add(targetedIntent)
            }
        }

        if (targetedIntents.isNotEmpty()) {
            val chooser = Intent.createChooser(targetedIntents.removeAt(0), context.getString(R.string.share)).apply {
                if (targetedIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedIntents.toTypedArray())
                }
            }
            context.startActivity(chooser)
        } else {
            context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.share)))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.share)))
    }
}
