package com.avalibeyaz.evrak.ui

import android.app.Activity
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
                val activity = context?.findActivity()
                if (activity != null) {
                    convertWordToPdfWithLibreOffice(inputFile, outputFile, activity)
                } else {
                    val errorMsg = context?.getString(R.string.error_activity_required) ?: "Activity is required for Word conversion."
                    ConversionResult.Error(errorMsg)
                }
            }
            "html", "htm" -> {
                if (context != null) {
                    convertHtmlToPdfWithWebView(inputFile, outputFile, context)
                } else {
                    val errorMsg = context?.getString(R.string.error_context_required) ?: "Context is required for HTML conversion."
                    ConversionResult.Error(errorMsg)
                }
            }
            else -> {
                val errorMsg = context?.getString(R.string.error_unsupported_type, inputFile.extension) 
                    ?: "Unsupported file type: .${inputFile.extension}"
                ConversionResult.Error(errorMsg)
            }
        }
    }

    suspend fun convertHtmlToPdfWithWebView(
        inputFile: File,
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
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            offscreenPreRaster = true
        }

        // webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

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
                                            val msg = context.getString(R.string.error_pdf_close_failed, e.message)
                                            deferred.complete(ConversionResult.Error(msg))
                                        }
                                    },
                                    onFailure = { error ->
                                        pfd.close()
                                        val msg = context.getString(R.string.error_pdf_write_failed, error)
                                        deferred.complete(ConversionResult.Error(msg))
                                    }
                                )
                                adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), pfd, null, writeCallback)
                            },
                            onFailure = { error ->
                                pfd.close()
                                val msg = context.getString(R.string.error_pdf_layout_failed, error)
                                deferred.complete(ConversionResult.Error(msg))
                            }
                        )
                        adapter.onLayout(null, printAttributes, null, layoutCallback, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "PDF conversion error", e)
                        if (!deferred.isCompleted) {
                            val msg = context.getString(R.string.error_pdf_creation_failed, e.localizedMessage)
                            deferred.complete(ConversionResult.Error(msg))
                        }
                    }
                }, 3500)
            }
        }

        try {
            val htmlContent = inputFile.readText(Charsets.UTF_8)
            webView.loadDataWithBaseURL("https://evrak.app/", htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            if (!deferred.isCompleted) {
                val msg = context.getString(R.string.error_file_read_failed, e.message)
                deferred.complete(ConversionResult.Error(msg))
            }
        }

        try {
            withTimeout(35000) { deferred.await() }
        } catch (e: Exception) {
            webView.stopLoading()
            if (deferred.isCompleted) {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                deferred.getCompleted()
            } else {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    ConversionResult.Error(context.getString(R.string.error_conversion_timeout))
                } else {
                    val msg = context.getString(R.string.error_during_conversion) + ": ${e.localizedMessage ?: ""}"
                    ConversionResult.Error(msg)
                }
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

                val tempHtmlFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.html")
                tempHtmlFile.writeText(html, Charsets.UTF_8)

                val result = convertHtmlToPdfWithWebView(tempHtmlFile, outputFile, context)
                tempHtmlFile.delete()
                result
            } catch (e: Exception) {
                Log.e(TAG, "UDF -> PDF conversion error", e)
                ConversionResult.Error(context.getString(R.string.error_udf_conversion_failed, e.message))
            }
        }
    }

    fun convertTiffToPdf(inputFile: File, outputFile: File, context: Context? = null): ConversionResult {
        val pdfDocument = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var tiffRenderer: TiffRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            tiffRenderer = TiffRenderer(pfd)
            for (pageIndex in 0 until tiffRenderer.pageCount) {
                val page = tiffRenderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(TiffBitmap(bitmap), null, null, TiffRenderMode.FOR_DISPLAY)
                val pdfPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex + 1).create())
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(pdfPage)
                page.close(); bitmap.recycle()
            }
            writePdf(pdfDocument, outputFile)
            return ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            val msg = context?.getString(R.string.error_tiff_error, e.message) ?: "TIFF error: ${e.message}"
            return ConversionResult.Error(msg, e)
        } finally {
            tiffRenderer?.close(); pfd?.close(); pdfDocument.close()
        }
    }

    private suspend fun convertWordToPdfWithLibreOffice(
        inputFile: File,
        outputFile: File,
        activity: Activity
    ): ConversionResult {
        return withContext(Dispatchers.IO) {
            try {
                val success = LibreOfficeManager.convertToPdf(inputFile, outputFile, activity)
                if (success) {
                    ConversionResult.Success(outputFile)
                } else {
                    ConversionResult.Error(activity.getString(R.string.error_libreoffice_failed))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Word -> PDF conversion error", e)
                ConversionResult.Error(activity.getString(R.string.error_word_conversion_failed, e.message))
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
