package com.avalibeyaz.evrak.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.print.PrintResultCallback
import com.avalibeyaz.evrak.R
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

object DocumentConverter {

    private const val TAG = "DocumentConverter"

    sealed class ConversionResult {
        data class Success(val outputFile: File) : ConversionResult()
        data class Error(val message: String, val cause: Throwable? = null) : ConversionResult()
    }

    suspend fun convert(inputFile: File, outputFile: File, context: Context? = null): ConversionResult {
        if (!inputFile.exists()) {
            val errorMsg = context?.getString(R.string.error_input_file_not_found, inputFile.absolutePath)
                ?: "Input file not found: ${inputFile.absolutePath}"
            return ConversionResult.Error(errorMsg)
        }

        val ext = inputFile.extension.lowercase()
        if (ext in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")) {
            if (!validateOfficeFile(inputFile)) {
                val errorMsg = context?.let { FileErrorUtils.getStandardizedErrorMessage(it, "Invalid or corrupted office file") }
                    ?: "Invalid or corrupted office file"
                return ConversionResult.Error(errorMsg)
            }
        }

        return when (ext) {
            "tif", "tiff" -> convertTiffToPdf(inputFile, outputFile, context)
            "udf" -> {
                if (context != null) {
                    convertUdfToPdf(inputFile, outputFile, context)
                } else {
                    ConversionResult.Error("Context is required for UDF conversion.")
                }
            }
            "doc", "docx", "ppt", "pptx" -> {
                if (context != null) {
                    convertWordToPdfWithLibreOffice(inputFile, outputFile, context)
                } else {
                    val errorMsg = "Context is required for Office conversion."
                    ConversionResult.Error(errorMsg)
                }
            }
            "xls", "xlsx" -> {
                if (context != null) {
                    convertExcelToPdfWithWebView(inputFile, outputFile, context)
                } else {
                    val errorMsg = "Context is required for Excel conversion."
                    ConversionResult.Error(errorMsg)
                }
            }
            "html", "htm" -> {
                if (context != null) {
                    convertHtmlFileToPdf(inputFile, outputFile, context)
                } else {
                    val errorMsg = context?.getString(R.string.error_context_required) ?: "Context is required for HTML conversion."
                    ConversionResult.Error(errorMsg)
                }
            }
            "txt" -> {
                if (context != null) {
                    convertTxtToPdf(inputFile, outputFile, context)
                } else {
                    ConversionResult.Error("Context is required for TXT conversion.")
                }
            }
            else -> {
                val errorMsg = context?.getString(R.string.error_unsupported_type, inputFile.extension)
                    ?: "Unsupported file type: .${inputFile.extension}"
                ConversionResult.Error(errorMsg)
            }
        }
    }

    suspend fun convertToHtml(inputFile: File, outputFile: File, context: Context): ConversionResult {
        if (!inputFile.exists()) return ConversionResult.Error("File not found")
        return withContext(Dispatchers.IO) {
            try {
                val success = LibreOfficeManager.convertToHtml(inputFile, outputFile, context)
                if (success) ConversionResult.Success(outputFile)
                else ConversionResult.Error("LibreOffice HTML conversion failed")
            } catch (e: Exception) {
                ConversionResult.Error(e.localizedMessage ?: "HTML conversion error")
            }
        }
    }

    suspend fun convertHtmlFileToPdf(inputFile: File, outputFile: File, context: Context): ConversionResult {
        return try {
            val html = inputFile.readText(Charsets.UTF_8)
            convertHtmlToPdfWithWebView(html, outputFile, context)
        } catch (e: Exception) {
            ConversionResult.Error("HTML read error: ${e.message}")
        }
    }

    suspend fun convertHtmlToPdfWithWebView(
        htmlContent: String,
        outputFile: File,
        context: Context
    ): ConversionResult = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<ConversionResult>()
        val webView = WebView(context)
        var activePfd: ParcelFileDescriptor? = null

        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            loadWithOverviewMode = false
            useWideViewPort = true
            @Suppress("DEPRECATION")
            textZoom = 100
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (deferred.isCompleted) return@postDelayed
                    try {
                        val printAttributes = android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 720, 720))
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build()

                        val adapter = webView.createPrintDocumentAdapter(context.getString(R.string.print_adapter_name))
                        val pfd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
                        activePfd = pfd

                        val layoutCallback = PrintResultCallback.createLayoutCallback(
                            onSuccess = { _, _ ->
                                val writeCallback = PrintResultCallback.createWriteCallback(
                                    onSuccess = {
                                        try {
                                            pfd.close()
                                            activePfd = null
                                            deferred.complete(ConversionResult.Success(outputFile))
                                        } catch (e: Exception) {
                                            deferred.complete(ConversionResult.Error("PDF close error"))
                                        }
                                    },
                                    onFailure = { error ->
                                        try { pfd.close() } catch (_: Exception) {}
                                        activePfd = null
                                        deferred.complete(ConversionResult.Error("PDF write failed: $error"))
                                    }
                                )
                                adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), pfd, null, writeCallback)
                            },
                            onFailure = { error ->
                                try { pfd.close() } catch (_: Exception) {}
                                activePfd = null
                                deferred.complete(ConversionResult.Error("PDF layout failed: $error"))
                            }
                        )
                        adapter.onLayout(null, printAttributes, null, layoutCallback, null)
                    } catch (e: Exception) {
                        if (!deferred.isCompleted) {
                            deferred.complete(ConversionResult.Error("PDF creation failed: ${e.message}"))
                        }
                    }
                }, 3500)
            }
        }

        var finalHtml = htmlContent
        val fontBase64 = try {
            context.assets.open("unpack/user/fonts/LiberationSerif-Regular.ttf").use { input ->
                android.util.Base64.encodeToString(input.readBytes(), android.util.Base64.NO_WRAP)
            }
        } catch (e: Exception) { null }

        val csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:\">"
        val fontStyle = if (fontBase64 != null) {
            "<style>@font-face { font-family: 'EmbeddedLiberation'; src: url(data:font/ttf;base64,$fontBase64) format('truetype'); } * { font-family: 'EmbeddedLiberation', serif !important; }</style>"
        } else ""
        val injected = "$csp$fontStyle"

        finalHtml = if (finalHtml.contains("<head>", ignoreCase = true)) {
            finalHtml.replaceFirst("<head>", "<head>$injected", ignoreCase = true)
        } else {
            "$injected$finalHtml"
        }

        webView.loadDataWithBaseURL("https://evrak.app/", finalHtml, "text/html", "UTF-8", null)

        try {
            withTimeout(60000) { deferred.await() }
        } catch (e: Exception) {
            webView.stopLoading()
            try { activePfd?.close() } catch (_: Exception) {}
            if (deferred.isCompleted) {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                deferred.getCompleted()
            } else {
                ConversionResult.Error(context.getString(R.string.error_conversion_timeout))
            }
        } finally {
            try { webView.destroy() } catch (_: Exception) {}
        }
    }

    suspend fun convertUdfToPdf(inputFile: File, outputFile: File, context: Context): ConversionResult {
        return withContext(Dispatchers.IO) {
            try {
                val html = UdfToHtmlConverter.convertUdfToHtml(inputFile, context)
                if (html.isEmpty()) {
                    return@withContext ConversionResult.Error(context.getString(R.string.error_udf_parse_failed))
                }
                convertHtmlToPdfWithWebView(html, outputFile, context)
            } catch (e: Exception) {
                Log.e(TAG, "UDF -> PDF conversion error", e)
                ConversionResult.Error(context.getString(R.string.error_udf_conversion_failed, e.message))
            }
        }
    }

    suspend fun convertTxtToPdf(inputFile: File, outputFile: File, context: Context): ConversionResult {
        return try {
            val text = inputFile.readText(Charsets.UTF_8)
            val escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val html = "<html><body style='white-space: pre-wrap; font-family: monospace;'>$escapedText</body></html>"
            convertHtmlToPdfWithWebView(html, outputFile, context)
        } catch (e: Exception) {
            ConversionResult.Error("TXT error: ${e.message}")
        }
    }

    private suspend fun convertExcelToPdfWithWebView(
        inputFile: File,
        outputFile: File,
        context: Context
    ): ConversionResult {
        return withContext(Dispatchers.IO) {
            val tempHtml = File(context.cacheDir, "excel_print_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.html")
            try {
                val htmlResult = convertToHtml(inputFile, tempHtml, context)
                if (htmlResult is ConversionResult.Success) {
                    var htmlContent = tempHtml.readText(Charsets.UTF_8)

                    val css = """
                        <style>
                            table { border-collapse: collapse; width: auto; min-width: 100%; margin-bottom: 20px; table-layout: auto; }
                            th, td { border: 1px solid #ccc; padding: 8px; text-align: left; white-space: nowrap; }
                            th { background-color: #f2f2f2; }
                            body { font-family: sans-serif; padding: 10px; }
                            img { max-width: 100%; height: auto; }
                        </style>
                    """.trimIndent()

                    if (htmlContent.contains("<head>", ignoreCase = true)) {
                        htmlContent = htmlContent.replace("<head>", "<head>$css", ignoreCase = true)
                    } else {
                        htmlContent = "<html><head>$css</head><body>$htmlContent</body></html>"
                    }

                    convertHtmlToPdfWithWebView(htmlContent, outputFile, context)
                } else {
                    htmlResult
                }
            } catch (e: Exception) {
                ConversionResult.Error("Excel print conversion error: ${e.localizedMessage}")
            } finally {
                try { if (tempHtml.exists()) tempHtml.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun validateOfficeFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return try {
            when (ext) {
                "docx", "xlsx", "pptx" -> {
                    java.util.zip.ZipFile(file).use { zip ->
                        val entries = zip.entries().asSequence().map { it.name }.toSet()
                        when (ext) {
                            "docx" -> entries.contains("word/document.xml")
                            "xlsx" -> entries.contains("xl/workbook.xml")
                            "pptx" -> entries.contains("ppt/presentation.xml")
                            else -> true
                        }
                    }
                }
                "doc", "xls", "ppt" -> {
                    if (file.length() < 512) return false
                    val bytes = file.readBytes()
                    if (bytes.size >= 4) {
                        bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
                                bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
                    } else {
                        false
                    }
                }
                else -> true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun convertTiffToPdf(inputFile: File, outputFile: File, context: Context? = null): ConversionResult {
        val pdfDocument = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var tiffRenderer: TiffRenderer? = null
        try {
            var pages = 0
            try {
                pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                tiffRenderer = TiffRenderer(pfd)
                pages = tiffRenderer.pageCount()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                tiffRenderer = null
            }

            if (tiffRenderer != null && pages > 0) {
                for (pageIndex in 0 until pages) {
                    var bitmap: Bitmap? = null
                    try {
                        val page = tiffRenderer.openPage(pageIndex)
                        try {
                            val b = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            page.render(TiffBitmap(b), null, null, TiffRenderMode.FOR_DISPLAY)
                            bitmap = b
                        } finally {
                            try { page.close() } catch (_: Exception) {}
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        bitmap = decodeJpegTiffPage(inputFile, pageIndex)
                    }

                    if (bitmap != null) {
                        val pdfPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex + 1).create())
                        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdfDocument.finishPage(pdfPage)
                        bitmap.recycle()
                    } else {
                        throw IllegalStateException("Failed to decode page $pageIndex")
                    }
                }
            } else {
                var pageIndex = 0
                while (true) {
                    val bitmap = decodeJpegTiffPage(inputFile, pageIndex) ?: break
                    val pdfPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex + 1).create())
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    bitmap.recycle()
                    pageIndex++
                }
                if (pageIndex == 0) {
                    throw IllegalStateException("Failed to parse TIFF file")
                }
            }

            writePdf(pdfDocument, outputFile)
            return ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            val msg = context?.let { FileErrorUtils.getStandardizedErrorMessage(it, e) } ?: "TIFF error: ${e.message}"
            return ConversionResult.Error(msg)
        } finally {
            try { tiffRenderer?.close() } catch (_: Exception) {}
            pfd?.close(); pdfDocument.close()
        }
    }

    private fun decodeJpegTiffPage(file: File, targetIndex: Int): Bitmap? {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                if (input.read(header) < 8) return@use null
                val isLe = header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte()
                val magic = if (isLe) {
                    ((header[3].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)
                } else {
                    ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
                }
                if (magic != 42) return@use null

                var ifdOffset = if (isLe) {
                    ((header[7].toInt() and 0xFF) shl 24) or
                    ((header[6].toInt() and 0xFF) shl 16) or
                    ((header[5].toInt() and 0xFF) shl 8) or
                    (header[4].toInt() and 0xFF)
                } else {
                    ((header[4].toInt() and 0xFF) shl 24) or
                    ((header[5].toInt() and 0xFF) shl 16) or
                    ((header[6].toInt() and 0xFF) shl 8) or
                    (header[7].toInt() and 0xFF)
                }

                var currentIndex = 0
                var stripOffsets = listOf<Long>()
                var stripByteCounts = listOf<Long>()

                while (ifdOffset != 0) {
                    input.channel.position(ifdOffset.toLong())
                    val countBuf = ByteArray(2)
                    if (input.read(countBuf) < 2) break
                    val numEntries = if (isLe) {
                        ((countBuf[1].toInt() and 0xFF) shl 8) or (countBuf[0].toInt() and 0xFF)
                    } else {
                        ((countBuf[0].toInt() and 0xFF) shl 8) or (countBuf[1].toInt() and 0xFF)
                    }

                    val entryBuffer = ByteArray(12)
                    var localOffsets = listOf<Long>()
                    var localByteCounts = listOf<Long>()

                    for (i in 0 until numEntries) {
                        if (input.read(entryBuffer) < 12) break
                        val tag = if (isLe) {
                            ((entryBuffer[1].toInt() and 0xFF) shl 8) or (entryBuffer[0].toInt() and 0xFF)
                        } else {
                            ((entryBuffer[0].toInt() and 0xFF) shl 8) or (entryBuffer[1].toInt() and 0xFF)
                        }
                        val type = if (isLe) {
                            ((entryBuffer[3].toInt() and 0xFF) shl 8) or (entryBuffer[2].toInt() and 0xFF)
                        } else {
                            ((entryBuffer[2].toInt() and 0xFF) shl 8) or (entryBuffer[3].toInt() and 0xFF)
                        }
                        val count = if (isLe) {
                            ((entryBuffer[7].toInt() and 0xFF) shl 24) or
                            ((entryBuffer[6].toInt() and 0xFF) shl 16) or
                            ((entryBuffer[5].toInt() and 0xFF) shl 8) or
                            (entryBuffer[4].toInt() and 0xFF)
                        } else {
                            ((entryBuffer[4].toInt() and 0xFF) shl 24) or
                            ((entryBuffer[5].toInt() and 0xFF) shl 16) or
                            ((entryBuffer[6].toInt() and 0xFF) shl 8) or
                            (entryBuffer[7].toInt() and 0xFF)
                        }
                        val valOrOffset = if (isLe) {
                            ((entryBuffer[11].toInt() and 0xFF) shl 24) or
                            ((entryBuffer[10].toInt() and 0xFF) shl 16) or
                            ((entryBuffer[9].toInt() and 0xFF) shl 8) or
                            (entryBuffer[8].toInt() and 0xFF)
                        } else {
                            ((entryBuffer[8].toInt() and 0xFF) shl 24) or
                            ((entryBuffer[9].toInt() and 0xFF) shl 16) or
                            ((entryBuffer[10].toInt() and 0xFF) shl 8) or
                            (entryBuffer[11].toInt() and 0xFF)
                        }

                        when (tag) {
                            273 -> localOffsets = readTiffValues(input, isLe, type, count.toLong(), valOrOffset)
                            279 -> localByteCounts = readTiffValues(input, isLe, type, count.toLong(), valOrOffset)
                        }
                    }

                    if (currentIndex == targetIndex) {
                        stripOffsets = localOffsets
                        stripByteCounts = localByteCounts
                        break
                    }

                    val nextIfdBuf = ByteArray(4)
                    if (input.read(nextIfdBuf) < 4) break
                    ifdOffset = if (isLe) {
                        ((nextIfdBuf[3].toInt() and 0xFF) shl 24) or
                        ((nextIfdBuf[2].toInt() and 0xFF) shl 16) or
                        ((nextIfdBuf[1].toInt() and 0xFF) shl 8) or
                        (nextIfdBuf[0].toInt() and 0xFF)
                    } else {
                        ((nextIfdBuf[0].toInt() and 0xFF) shl 24) or
                        ((nextIfdBuf[1].toInt() and 0xFF) shl 16) or
                        ((nextIfdBuf[2].toInt() and 0xFF) shl 8) or
                        (nextIfdBuf[3].toInt() and 0xFF)
                    }
                    currentIndex++
                }

                if (stripOffsets.isNotEmpty() && stripByteCounts.isNotEmpty()) {
                    val offset = stripOffsets[0]
                    val length = stripByteCounts[0].toInt()
                    val jpegBytes = ByteArray(length)
                    val fis = input as? java.io.FileInputStream
                    fis?.channel?.position(offset)
                    input.read(jpegBytes)
                    return@use android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, length)
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readTiffValues(input: java.io.InputStream, isLe: Boolean, type: Int, count: Long, valOrOffset: Int): List<Long> {
        val result = mutableListOf<Long>()
        val fis = input as? java.io.FileInputStream
        if (count == 1L) {
            result.add(valOrOffset.toLong() and 0xFFFFFFFFL)
        } else {
            val currentPos = fis?.channel?.position() ?: 0L
            fis?.channel?.position(valOrOffset.toLong() and 0xFFFFFFFFL)
            val byteSize = when (type) {
                3 -> 2
                4 -> 4
                else -> 4
            }
            val buf = ByteArray((count * byteSize).toInt())
            input.read(buf)
            for (i in 0 until count.toInt()) {
                val v = if (byteSize == 2) {
                    val idx = i * 2
                    if (isLe) ((buf[idx+1].toInt() and 0xFF) shl 8) or (buf[idx].toInt() and 0xFF)
                    else ((buf[idx].toInt() and 0xFF) shl 8) or (buf[idx+1].toInt() and 0xFF)
                } else {
                    val idx = i * 4
                    if (isLe) {
                        ((buf[idx+3].toInt() and 0xFF) shl 24) or ((buf[idx+2].toInt() and 0xFF) shl 16) or ((buf[idx+1].toInt() and 0xFF) shl 8) or (buf[idx].toInt() and 0xFF)
                    } else {
                        ((buf[idx].toInt() and 0xFF) shl 24) or ((buf[idx+1].toInt() and 0xFF) shl 16) or ((buf[idx+2].toInt() and 0xFF) shl 8) or (buf[idx+3].toInt() and 0xFF)
                    }
                }
                result.add(v.toLong() and 0xFFFFFFFFL)
            }
            fis?.channel?.position(currentPos)
        }
        return result
    }

    private suspend fun convertWordToPdfWithLibreOffice(
        inputFile: File,
        outputFile: File,
        context: Context
    ): ConversionResult {
        return withContext(Dispatchers.IO) {
            try {
                val success = LibreOfficeManager.convertToPdf(inputFile, outputFile, context)
                if (success) ConversionResult.Success(outputFile)
                else ConversionResult.Error(FileErrorUtils.getStandardizedErrorMessage(context, "LibreOffice failed"))
            } catch (e: Exception) {
                ConversionResult.Error(FileErrorUtils.getStandardizedErrorMessage(context, e))
            }
        }
    }

    private fun writePdf(d: PdfDocument, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { d.writeTo(it) } }

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share)))
        } catch (e: Exception) { e.printStackTrace() }
    }
}