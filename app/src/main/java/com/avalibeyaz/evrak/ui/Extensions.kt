package com.avalibeyaz.evrak.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

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
        else -> "application/octet-stream"
    }
}
