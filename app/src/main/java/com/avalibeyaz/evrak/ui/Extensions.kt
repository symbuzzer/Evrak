package com.avalibeyaz.evrak.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.avalibeyaz.evrak.R
import java.io.File

private const val FEEDBACK_EMAIL = "alipolatbeyaz@gmail.com"

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun Intent.documentUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
    else -> null
}

fun getMimeType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    if (ext == "apk") return "application/vnd.android.package-archive"

    val mimeFromMap = if (ext.isNotEmpty()) {
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    } else null

    if (mimeFromMap != null) return mimeFromMap

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

fun isEvrakSupportedFormat(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".pdf") ||
            lower.endsWith(".docx") || lower.endsWith(".doc") ||
            lower.endsWith(".xlsx") || lower.endsWith(".xls") ||
            lower.endsWith(".pptx") || lower.endsWith(".ppt") ||
            lower.endsWith(".tif") || lower.endsWith(".tiff") ||
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") ||
            lower.endsWith(".heic") || lower.endsWith(".avif") ||
            lower.endsWith(".udf") ||
            lower.endsWith(".html") || lower.endsWith(".htm") ||
            lower.endsWith(".txt") || lower.endsWith(".evraklog") ||
            lower.endsWith(".zip") || lower.endsWith(".eyp")
}

fun openFileWithSystemChooser(context: Context, filePath: String): Boolean {
    val file = File(filePath)
    if (!file.exists()) return false

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val mimeType = getMimeType(filePath)
    val ext = filePath.substringAfterLast('.', "").lowercase()

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    val activities = context.packageManager.queryIntentActivities(intent, 0)
    if (activities.isNotEmpty()) {
        for (resolveInfo in activities) {
            val pkg = resolveInfo.activityInfo.packageName
            try {
                context.grantUriPermission(
                    pkg,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        if (ext == "apk" || mimeType == "application/vnd.android.package-archive") {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.open_with)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(chooserIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return false
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
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
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

val fileNameVisualTransformation = VisualTransformation { text ->
    val originalText = text.text
    val sb = StringBuilder()
    val offsetMappingList = IntArray(originalText.length + 1)
    
    var transformedIndex = 0
    for (i in originalText.indices) {
        offsetMappingList[i] = transformedIndex
        sb.append(originalText[i])
        transformedIndex++
        
        val c = originalText[i]
        if (c == '_' || c == '-' || c == '.' || ((i + 1) % 15 == 0 && i + 1 < originalText.length)) {
            sb.append('\u200B')
            transformedIndex++
        }
    }
    offsetMappingList[originalText.length] = transformedIndex
    
    val transformedText = sb.toString()
    TransformedText(
        text = AnnotatedString(transformedText),
        offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset >= originalText.length) return transformedText.length
                return offsetMappingList[offset.coerceIn(0, originalText.length)]
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset >= transformedText.length) return originalText.length
                var original = 0
                for (j in offsetMappingList.indices) {
                    if (offsetMappingList[j] <= offset) {
                        original = j
                    } else {
                        break
                    }
                }
                return original
            }
        }
    )
}

fun printFile(
    context: Context,
    filePath: String,
    displayName: String,
    onConvertingChange: (Boolean) -> Unit = {}
) {
    val file = File(filePath)
    if (!file.exists()) return

    val isImage = filePath.endsWith(".jpg", true) ||
            filePath.endsWith(".jpeg", true) ||
            filePath.endsWith(".png", true) ||
            filePath.endsWith(".gif", true) ||
            filePath.endsWith(".webp", true) ||
            filePath.endsWith(".bmp", true) ||
            filePath.endsWith(".heic", true) ||
            filePath.endsWith(".avif", true)

    if (filePath.endsWith(".pdf", true)) {
        onConvertingChange(true)
        try {
            doPrint(context, file, displayName)
        } finally {
            onConvertingChange(false)
        }
    } else if (isImage) {
        onConvertingChange(true)
        try {
            doPrintImage(context, file, displayName)
        } finally {
            onConvertingChange(false)
        }
    } else {
        val activity = context.findActivity() as? ComponentActivity
        if (activity != null) {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { onConvertingChange(true) }
                try {
                    val tempPdf = File(context.cacheDir, "print_temp_${System.currentTimeMillis()}.pdf")
                    val result = DocumentConverter.convert(file, tempPdf, context)
                    if (result is DocumentConverter.ConversionResult.Success) {
                        withContext(Dispatchers.Main) {
                            doPrint(context, tempPdf, displayName)
                        }
                    } else if (result is DocumentConverter.ConversionResult.Error) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.error_conversion_failed, result.message), Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) { onConvertingChange(false) }
                }
            }
        }
    }
}

private fun doPrintImage(context: Context, file: File, displayName: String) {
    val printHelper = PrintHelper(context)
    printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
    if (bitmap != null) {
        printHelper.printBitmap(displayName, bitmap)
    }
}

private fun doPrint(context: Context, file: File, displayName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
    val jobName = "${context.getString(R.string.app_name)} - $displayName"

    printManager.print(
        jobName,
        object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val info = android.print.PrintDocumentInfo.Builder(displayName)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                var input: java.io.FileInputStream? = null
                var output: java.io.FileOutputStream? = null
                try {
                    input = java.io.FileInputStream(file)
                    output = java.io.FileOutputStream(destination?.fileDescriptor)
                    input.copyTo(output)
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback?.onWriteFailed(e.message)
                } finally {
                    try { input?.close() } catch (_: Exception) {}
                }
            }
        },
        null
    )
}