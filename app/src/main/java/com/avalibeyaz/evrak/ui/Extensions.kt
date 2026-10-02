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
