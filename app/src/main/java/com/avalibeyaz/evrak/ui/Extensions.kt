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
        path.endsWith(".tif", ignoreCase = true) || path.endsWith(".tiff", ignoreCase = true) -> "image/tiff"
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".gif", true) -> "image/gif"
        path.endsWith(".udf", true) -> "application/x-udf"
        path.endsWith(".html", true) || path.endsWith(".htm", true) -> "text/html"
        else -> "application/octet-stream"
    }
}
