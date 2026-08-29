package com.avalibeyaz.evrak.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.TabStopSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.print.PrintResultCallback
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * TIFF, UDF, Word ve HTML dosyalarını PDF'ye dönüştürür.
 */
object DocumentConverter {

    private const val TAG = "DocumentConverter"
    private const val DEFAULT_COLOR = -16777216 // siyah

    sealed class ConversionResult {
        data class Success(val outputFile: File) : ConversionResult()
        data class Error(val message: String, val cause: Throwable? = null) : ConversionResult()
    }

    /** Dosya uzantısına bakarak uygun dönüştürücüyü seçer. */
    suspend fun convert(inputFile: File, outputFile: File, context: android.content.Context? = null): ConversionResult {
        if (!inputFile.exists()) {
            return ConversionResult.Error("Girdi dosyası bulunamadı: ${inputFile.absolutePath}")
        }
        return when (inputFile.extension.lowercase()) {
            "tif", "tiff" -> convertTiffToPdf(inputFile, outputFile)
            "udf" -> {
                if (context != null) {
                    convertUdfToPdf(inputFile, outputFile, context)
                } else {
                    ConversionResult.Error("UDF dönüştürme için context gereklidir.")
                }
            }
            "doc", "docx" -> convertWordToPdf(inputFile, outputFile)
            "html", "htm" -> {
                if (context != null) {
                    convertHtmlToPdfWithWebView(inputFile, outputFile, context)
                } else {
                    ConversionResult.Error("HTML dönüştürme için context gereklidir.")
                }
            }
            else -> ConversionResult.Error("Desteklenmeyen dosya türü: .${inputFile.extension}")
        }
    }

    /**
     * WebView kullanarak HTML'den birebir PDF üretir (Yüksek Kalite).
     */
    suspend fun convertHtmlToPdfWithWebView(
        inputFile: File,
        outputFile: File,
        context: android.content.Context
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
        }

        webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (deferred.isCompleted) return@postDelayed
                    try {
                        val printAttributes = android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build()

                        val adapter = webView.createPrintDocumentAdapter("Evrak-Dönüşüm")
                        val pfd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

                        val layoutCallback = PrintResultCallback.createLayoutCallback(
                            onSuccess = { _, _ ->
                                val writeCallback = PrintResultCallback.createWriteCallback(
                                    onSuccess = {
                                        try {
                                            pfd.close()
                                            deferred.complete(ConversionResult.Success(outputFile))
                                        } catch (e: Exception) {
                                            deferred.complete(ConversionResult.Error("PDF kapatılamadı: ${e.message}"))
                                        }
                                    },
                                    onFailure = { error ->
                                        pfd.close()
                                        deferred.complete(ConversionResult.Error("PDF yazma hatası: $error"))
                                    }
                                )
                                adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), pfd, null, writeCallback)
                            },
                            onFailure = { error ->
                                pfd.close()
                                deferred.complete(ConversionResult.Error("PDF yerleşim hatası: $error"))
                            }
                        )
                        adapter.onLayout(null, printAttributes, null, layoutCallback, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "PDF conversion error", e)
                        if (!deferred.isCompleted) deferred.complete(ConversionResult.Error("PDF oluşturma hatası: ${e.localizedMessage}"))
                    }
                }, 2000)
            }
        }

        try {
            val htmlContent = inputFile.readText(Charsets.UTF_8)
            webView.loadDataWithBaseURL("https://evrak.app/", htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            if (!deferred.isCompleted) deferred.complete(ConversionResult.Error("Dosya okuma hatası: ${e.message}"))
        }

        try {
            withTimeout(35000) { deferred.await() }
        } catch (e: Exception) {
            webView.stopLoading()
            if (deferred.isCompleted) {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                deferred.getCompleted()
            } else {
                if (e is kotlinx.coroutines.TimeoutCancellationException) ConversionResult.Error("Dönüştürme zaman aşımına uğradı.")
                else ConversionResult.Error("Dönüştürme hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}")
            }
        }
    }

    /**
     * UDF -> PDF Dönüşümü.
     * UdfHtmlConverter ile HTML'e çevirir ve WebView motoruyla yüksek kaliteli PDF üretir.
     */
    suspend fun convertUdfToPdf(inputFile: File, outputFile: File, context: android.content.Context): ConversionResult {
        return withContext(Dispatchers.IO) {
            try {
                val html = UdfHtmlConverter.convertUdfToHtml(inputFile)
                if (html.isEmpty()) return@withContext ConversionResult.Error("UDF içeriği ayrıştırılamadı.")

                val tempHtmlFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.html")
                tempHtmlFile.writeText(html, Charsets.UTF_8)

                val result = convertHtmlToPdfWithWebView(tempHtmlFile, outputFile, context)
                tempHtmlFile.delete()
                result
            } catch (e: Exception) {
                Log.e(TAG, "UDF -> PDF dönüşüm hatası", e)
                ConversionResult.Error("UDF dönüştürme hatası: ${e.message}")
            }
        }
    }

    fun convertTiffToPdf(inputFile: File, outputFile: File): ConversionResult {
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
            return ConversionResult.Error("TIFF hatası: ${e.message}", e)
        } finally {
            tiffRenderer?.close(); pfd?.close(); pdfDocument.close()
        }
    }

    fun convertWordToPdf(inputFile: File, outputFile: File): ConversionResult {
        return try {
            val blocks = mutableListOf<Block>()
            FileInputStream(inputFile).use { fis ->
                if (inputFile.extension.lowercase() == "docx") {
                    val docx = org.apache.poi.xwpf.usermodel.XWPFDocument(fis)
                    docx.bodyElements.forEach { 
                        if (it is org.apache.poi.xwpf.usermodel.XWPFParagraph) blocks.add(parseDocxParagraph(it))
                        else if (it is org.apache.poi.xwpf.usermodel.XWPFTable) blocks.add(parseDocxTable(it))
                    }
                } else {
                    val doc = org.apache.poi.hwpf.HWPFDocument(fis)
                    val range = doc.range
                    for (i in 0 until range.numParagraphs()) {
                        val para = range.getParagraph(i)
                        if (!para.isInTable) blocks.add(parseDocParagraph(para))
                    }
                }
            }
            renderBlocksToPdf(blocks, PageFormat(595.28f, 841.89f, 72f, 72f, 72f, 72f), outputFile)
            ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            ConversionResult.Error("Word hatası: ${e.message}", e)
        }
    }

    private fun parseDocxParagraph(para: org.apache.poi.xwpf.usermodel.XWPFParagraph): Block.Para {
        val runs = para.runs.map { run ->
            TextRun(run.getCTR().tList.joinToString("") { it.stringValue ?: "" }, run.isBold, run.isItalic, run.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE, if (run.fontSize != -1) run.fontSize.toFloat() else 11f, try { android.graphics.Color.parseColor("#${run.color}") } catch (_: Exception) { DEFAULT_COLOR })
        }
        return Block.Para(runs, 0, para.indentationLeft / 20f, para.indentationRight / 20f, para.indentationFirstLine / 20f, para.spacingBefore / 20f, para.spacingAfter / 20f)
    }

    private fun parseDocxTable(table: org.apache.poi.xwpf.usermodel.XWPFTable): Block.Tbl {
        val rows = table.rows.map { row ->
            row.tableCells.map { cell ->
                CellData(try { cell.ctTc.tcPr.gridSpan.`val`.toInt() } catch (_: Exception) { 1 }, cell.bodyElements.mapNotNull { if (it is org.apache.poi.xwpf.usermodel.XWPFParagraph) parseDocxParagraph(it) else null })
            }
        }
        return Block.Tbl(List(rows.maxOfOrNull { it.size } ?: 0) { 100f }, rows)
    }

    private fun parseDocParagraph(para: org.apache.poi.hwpf.usermodel.Paragraph): Block.Para {
        val runs = (0 until para.numCharacterRuns()).map { i ->
            val run = para.getCharacterRun(i)
            TextRun(run.text(), run.isBold, run.isItalic, run.underlineCode != 0, run.fontSize.toFloat() / 2f, if (run.color != -1) (0xFF000000.toInt() or (0xFFFFFF and run.color)) else DEFAULT_COLOR)
        }
        return Block.Para(runs, 0, para.getIndentFromLeft().toFloat() / 20f, para.getIndentFromRight().toFloat() / 20f, para.getFirstLineIndent().toFloat() / 20f, para.getSpacingBefore().toFloat() / 20f, para.getSpacingAfter().toFloat() / 20f)
    }

    private fun renderBlocksToPdf(blocks: List<Block>, format: PageFormat, outputFile: File) {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(format.widthPt.toInt(), format.heightPt.toInt(), pageNumber).create())
        var y = format.topMargin
        val contentWidth = format.widthPt - format.leftMargin - format.rightMargin

        for (block in blocks) {
            if (block is Block.PageBreak) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(format.widthPt.toInt(), format.heightPt.toInt(), ++pageNumber).create())
                y = format.topMargin; continue
            }
            val h = renderBlock(null, block, format.leftMargin, y, contentWidth)
            if (y + h > format.heightPt - format.bottomMargin && y > format.topMargin) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(format.widthPt.toInt(), format.heightPt.toInt(), ++pageNumber).create())
                y = format.topMargin
            }
            renderBlock(page.canvas, block, format.leftMargin, y, contentWidth)
            y += h + 2f
        }
        pdfDocument.finishPage(page); writePdf(pdfDocument, outputFile); pdfDocument.close()
    }

    private fun renderBlock(canvas: android.graphics.Canvas?, block: Block, x: Float, y: Float, w: Float): Float = when (block) {
        is Block.Para -> renderParagraph(canvas, block, x, y, w)
        is Block.Img -> renderImage(canvas, block, x, y, w)
        is Block.Tbl -> renderTable(canvas, block, x, y, w)
        Block.PageBreak -> 0f
    }

    private fun renderParagraph(canvas: android.graphics.Canvas?, para: Block.Para, x: Float, y: Float, width: Float): Float {
        val ssb = SpannableStringBuilder()
        para.runs.forEach { run ->
            val start = ssb.length; ssb.append(run.text); val end = ssb.length
            if (end > start) {
                val tf = Typeface.create(run.fontFamily ?: "serif", when { run.bold && run.italic -> Typeface.BOLD_ITALIC; run.bold -> Typeface.BOLD; run.italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
                ssb.setSpan(TypefaceSpan(tf), start, end, 33)
                ssb.setSpan(AbsoluteSizeSpan(run.sizePt.toInt().coerceAtLeast(1), false), start, end, 33)
                ssb.setSpan(ForegroundColorSpan(run.colorArgb), start, end, 33)
                if (run.underline) ssb.setSpan(UnderlineSpan(), start, end, 33)
            }
        }
        while (ssb.isNotEmpty() && (ssb.last() == '\n' || ssb.last() == '\r')) ssb.delete(ssb.length - 1, ssb.length)
        if (ssb.isEmpty()) return 0f
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; density = 1.0f }
        val align = when (para.alignment) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        val first = (para.leftIndent + para.firstLineIndent).toInt(); val rest = para.leftIndent.toInt()
        if (first != 0 || rest != 0) ssb.setSpan(LeadingMarginSpan.Standard(first, rest), 0, ssb.length, 33)
        val layout = StaticLayout.Builder.obtain(ssb, 0, ssb.length, paint, (width - para.rightIndent).toInt().coerceAtLeast(1)).setAlignment(align).setLineSpacing(para.lineSpacing, 1.0f).setIncludePad(true).build()
        if (canvas != null) { canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore() }
        return layout.height.toFloat()
    }

    private fun renderImage(canvas: android.graphics.Canvas?, img: Block.Img, x: Float, y: Float, width: Float): Float {
        val b = img.bitmap ?: return 0f
        var w = img.widthPt; var h = img.heightPt
        if (w > width) { val s = width / w; w *= s; h *= s }
        if (canvas != null) canvas.drawBitmap(b, null, RectF(x, y, x + w, y + h), null)
        return h
    }

    private fun renderTable(canvas: android.graphics.Canvas?, table: Block.Tbl, x: Float, y: Float, width: Float): Float {
        val colWidths = table.columnSpans.map { it * (width / table.columnSpans.sum()) }
        var curY = y
        for (row in table.rows) {
            var rowH = 0f; var curX = x
            row.forEachIndexed { i, cell ->
                val cw = colWidths[i]; var ch = 0f
                cell.blocks.forEach { ch += renderBlock(null, it, 0f, 0f, cw - 10f) + 2f }
                if (ch + 10f > rowH) rowH = ch + 10f
                if (canvas != null) {
                    canvas.drawRect(curX, curY, curX + cw, curY + rowH, android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 0.5f })
                    var iy = curY + 5f; cell.blocks.forEach { iy += renderBlock(canvas, it, curX + 5f, iy, cw - 10f) + 2f }
                }
                curX += cw
            }
            curY += rowH
        }
        return curY - y
    }

    private fun writePdf(d: PdfDocument, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { d.writeTo(it) } }

    fun shareFile(context: android.content.Context, file: File, mimeType: String = "application/pdf") {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(com.avalibeyaz.evrak.R.string.share)))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private data class TextRun(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean, val sizePt: Float, val colorArgb: Int, val fontFamily: String? = null)
    private sealed class Block {
        data class Para(
            val runs: List<TextRun>,
            val alignment: Int,
            val leftIndent: Float,
            val rightIndent: Float,
            val firstLineIndent: Float,
            val spaceAbove: Float,
            val spaceBelow: Float,
            val lineSpacing: Float = 0f
        ) : Block()
        data class Img(val bitmap: android.graphics.Bitmap?, val widthPt: Float, val heightPt: Float) : Block()
        data class Tbl(val columnSpans: List<Float>, val rows: List<List<CellData>>) : Block()
        object PageBreak : Block()
    }
    private data class CellData(
        val colspan: Int,
        val blocks: List<Block>,
        val borderSpec: Int = 15,
        val fillColor: Int? = null,
        val verticalAlign: String? = null
    )
    private data class PageFormat(val widthPt: Float, val heightPt: Float, val leftMargin: Float, val rightMargin: Float, val topMargin: Float, val bottomMargin: Float)
}
