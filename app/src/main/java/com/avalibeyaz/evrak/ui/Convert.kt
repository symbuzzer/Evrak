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
        return when (inputFile.extension.lowercase()) {
            "tif", "tiff" -> convertTiffToPdf(inputFile, outputFile, context)
            "udf" -> {
                if (context != null) {
                    convertUdfToPdf(inputFile, outputFile, context)
                } else {
                    ConversionResult.Error("Context is required for UDF conversion.")
                }
            }
            "doc", "docx" -> {
                if (context != null) {
                    convertWordToPdfWithLibreOffice(inputFile, outputFile, context)
                } else {
                    val errorMsg = "Context is required for Word conversion."
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

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = false
            useWideViewPort = true
            @Suppress("DEPRECATION")
            textZoom = 100
            allowFileAccess = true
            allowContentAccess = true
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

                        val layoutCallback = PrintResultCallback.createLayoutCallback(
                            onSuccess = { _, _ ->
                                val writeCallback = PrintResultCallback.createWriteCallback(
                                    onSuccess = {
                                        try {
                                            pfd.close()
                                            deferred.complete(ConversionResult.Success(outputFile))
                                        } catch (e: Exception) {
                                            deferred.complete(ConversionResult.Error("PDF close error"))
                                        }
                                    },
                                    onFailure = { error ->
                                        pfd.close()
                                        deferred.complete(ConversionResult.Error("PDF write failed: $error"))
                                    }
                                )
                                adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), pfd, null, writeCallback)
                            },
                            onFailure = { error ->
                                pfd.close()
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

        if (fontBase64 != null) {
            val fontStyle = "<style>@font-face { font-family: 'EmbeddedLiberation'; src: url(data:font/ttf;base64,$fontBase64) format('truetype'); } * { font-family: 'EmbeddedLiberation', serif !important; }</style>"
            if (finalHtml.contains("<head>", ignoreCase = true)) {
                finalHtml = finalHtml.replace("<head>", "<head>$fontStyle", ignoreCase = true)
            } else {
                finalHtml = "$fontStyle$finalHtml"
            }
        }

        webView.loadDataWithBaseURL("https://evrak.app/", finalHtml, "text/html", "UTF-8", null)

        try {
            withTimeout(60000) { deferred.await() }
        } catch (e: Exception) {
            webView.stopLoading()
            if (deferred.isCompleted) {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                deferred.getCompleted()
            } else {
                ConversionResult.Error(context.getString(R.string.error_conversion_timeout))
            }
        }
    }

    suspend fun convertUdfToPdf(inputFile: File, outputFile: File, context: Context): ConversionResult {
        return withContext(Dispatchers.IO) {
            try {
                val html = UdfHtmlConverter.convertUdfToHtml(inputFile, context)
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

    suspend fun convertTiffToPdf(inputFile: File, outputFile: File, context: Context? = null): ConversionResult {
        val pdfDocument = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var tiffRenderer: TiffRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            tiffRenderer = TiffRenderer(pfd)
            val pages = tiffRenderer.pageCount()
            for (pageIndex in 0 until pages) {
                val page = tiffRenderer.openPage(pageIndex)
                try {
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(TiffBitmap(bitmap), null, null, TiffRenderMode.FOR_DISPLAY)
                    val pdfPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex + 1).create())
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    bitmap.recycle()
                } finally {
                    try { page.close() } catch (_: Exception) {}
                }
            }
            writePdf(pdfDocument, outputFile)
            return ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            return ConversionResult.Error("TIFF error: ${e.message}")
        } finally {
            try { tiffRenderer?.close() } catch (_: Exception) {}
            pfd?.close(); pdfDocument.close()
        }
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
                else ConversionResult.Error(context.getString(R.string.error_libreoffice_failed))
            } catch (e: Exception) {
                ConversionResult.Error("Word error: ${e.message}")
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
