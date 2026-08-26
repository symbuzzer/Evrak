package com.avalibeyaz.evrak.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
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
import org.w3c.dom.Node
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TIFF, UDF, Word ve HTML dosyalarını PDF'ye dönüştürür.
 */
object DocumentConverter {

    private const val TAG = "DocumentConverter"

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
            "udf" -> convertUdfToPdf(inputFile, outputFile)
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
     * Android Yazdırma Altyapısını (Print Framework) sessizce kullanarak aslına uygun çıktı üretir.
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
                // Sayfanın tamamen yüklenmesi ve resimlerin render edilmesi için kısa bir gecikme
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (deferred.isCompleted) return@postDelayed
                    try {
                        val printAttributes = android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build()

                        val adapter = webView.createPrintDocumentAdapter("Evrak-Dönüşüm")

                        val pfd = ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_READ_WRITE or
                                    ParcelFileDescriptor.MODE_CREATE or
                                    ParcelFileDescriptor.MODE_TRUNCATE
                        )

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
                        if (!deferred.isCompleted) {
                            deferred.complete(ConversionResult.Error("PDF oluşturma hatası: ${e.localizedMessage}"))
                        }
                    }
                }, 2000)
            }
            
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                val msg = error?.description?.toString() ?: "Bilinmeyen hata"
                Log.e(TAG, "WebView resource error: $msg")
            }
            
            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (!deferred.isCompleted) {
                    deferred.complete(ConversionResult.Error("HTML yükleme hatası: $description"))
                }
            }
        }
        
        try {
            val htmlContent = inputFile.readText(Charsets.UTF_8)
            // Use loadDataWithBaseURL as requested for robustness
            webView.loadDataWithBaseURL("https://evrak.app/", htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            if (!deferred.isCompleted) {
                deferred.complete(ConversionResult.Error("Dosya okuma hatası: ${e.message}"))
            }
        }

        try {
            // Strict timeout for robust conversion
            withTimeout(35000) {
                deferred.await()
            }
        } catch (e: Exception) {
            webView.stopLoading()
            if (deferred.isCompleted) {
                // If it completed right as timeout hit
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                deferred.getCompleted()
            } else {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    ConversionResult.Error("Dönüştürme zaman aşımına uğradı.")
                } else {
                    ConversionResult.Error("Dönüştürme hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}")
                }
            }
        }
    }


    // ==================================================================
    // TIFF -> PDF
    // ==================================================================

    fun convertTiffToPdf(inputFile: File, outputFile: File): ConversionResult {
        val pdfDocument = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var tiffRenderer: TiffRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            tiffRenderer = TiffRenderer(pfd)
            
            val pageCount = tiffRenderer.pageCount
            if (pageCount <= 0) {
                return ConversionResult.Error("TIFF içinde sayfa bulunamadı: ${inputFile.name}")
            }

            for (pageIndex in 0 until pageCount) {
                val page = tiffRenderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                val tiffBitmap = TiffBitmap(bitmap)
                page.render(tiffBitmap, null, null, TiffRenderMode.FOR_DISPLAY)
                
                val pageInfo = PdfDocument.PageInfo
                    .Builder(bitmap.width, bitmap.height, pageIndex + 1)
                    .create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(pdfPage)
                
                page.close()
                bitmap.recycle()
            }

            writePdf(pdfDocument, outputFile)
            return ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "TIFF -> PDF dönüştürme hatası", e)
            return ConversionResult.Error("TIFF dönüştürme hatası: ${e.message}", e)
        } finally {
            tiffRenderer?.close()
            pfd?.close()
            pdfDocument.close()
        }
    }

    // ==================================================================
    // UDF -> PDF (Block tabanlı motor)
    // ==================================================================

    private data class TextRun(
        val text: String,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val sizePt: Float,
        val colorArgb: Int,
        val fontFamily: String? = null
    )

    private sealed class Block {
        data class Para(
            val runs: List<TextRun>,
            val alignment: Int,
            val leftIndent: Float = 0f,
            val rightIndent: Float = 0f,
            val firstLineIndent: Float = 0f,
            val spaceAbove: Float = 0f,
            val spaceBelow: Float = 0f,
            val lineSpacing: Float = 0f
        ) : Block()
        data class Img(val bitmap: Bitmap?, val widthPt: Float, val heightPt: Float) : Block()
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

    private data class PageFormat(
        val widthPt: Float,
        val heightPt: Float,
        val leftMargin: Float,
        val rightMargin: Float,
        val topMargin: Float,
        val bottomMargin: Float
    )

    private const val DEFAULT_COLOR = -16777216 // siyah

    private val numberedListCounters = mutableMapOf<String, Int>()

    fun convertUdfToPdf(inputFile: File, outputFile: File): ConversionResult {
        numberedListCounters.clear()
        return try {
            val contentXmlBytes = ZipFile(inputFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .firstOrNull { it.name.equals("content.xml", ignoreCase = true) }
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".xml", true) }
                    ?: return ConversionResult.Error("UDF içinde content.xml bulunamadı: ${inputFile.name}")
                zip.getInputStream(entry).use { it.readBytes() }
            }

            val doc = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
            }.newDocumentBuilder().parse(contentXmlBytes.inputStream())

            val root = doc.documentElement ?: return ConversionResult.Error("Geçersiz UDF")
            val fullText = directChild(root, "content")?.textContent ?: return ConversionResult.Error("İçerik bulunamadı")

            val pageFormatNode = directChild(root, "properties")?.let { directChild(it, "pageFormat") }
            val pageFormat = PageFormat(
                widthPt = 595.28f, heightPt = 841.89f,
                leftMargin = pageFormatNode.attrFloat("leftMargin", 42.52f),
                rightMargin = pageFormatNode.attrFloat("rightMargin", 28.35f),
                topMargin = pageFormatNode.attrFloat("topMargin", 14.17f),
                bottomMargin = pageFormatNode.attrFloat("bottomMargin", 14.17f)
            )

            val elementsNode = directChild(root, "elements") ?: return ConversionResult.Error("Eleman bulunamadı")
            val blocks = parseBlocks(elementsNode, fullText)

            renderBlocksToPdf(blocks, pageFormat, outputFile)
            ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            ConversionResult.Error("UDF dönüştürme hatası: ${e.message}", e)
        }
    }

    private fun parseBlocks(container: Element, fullText: String): List<Block> {
        val blocks = mutableListOf<Block>()
        forEachChildElement(container) { child ->
            when (child.tagName) {
                "paragraph" -> blocks.addAll(parseParagraph(child, fullText))
                "table" -> blocks.add(parseTable(child, fullText))
                "page-break" -> blocks.add(Block.PageBreak)
                "header", "footer" -> blocks.addAll(parseBlocks(child, fullText))
            }
        }
        return blocks
    }

    private fun parseParagraph(node: Element, fullText: String): List<Block> {
        val alignment = node.getAttribute("Alignment").toIntOrNull() ?: 0
        val leftIndent = node.attrFloat("LeftIndent", 0f)
        val rightIndent = node.attrFloat("RightIndent", 0f)
        val firstLineIndent = node.attrFloat("FirstLineIndent", 0f)
        val spaceAbove = node.attrFloat("SpaceAbove", 0f)
        val spaceBelow = node.attrFloat("SpaceBelow", 0f)
        val lineSpacing = node.attrFloat("LineSpacing", 0f)

        val isNumbered = node.getAttribute("Numbered") == "true"
        val isBulleted = node.getAttribute("Bulleted") == "true"
        val listLevel = node.getAttribute("ListLevel").toIntOrNull() ?: 0
        
        val effectiveLeftIndent = if (isNumbered || isBulleted) leftIndent + (listLevel + 1) * 18f else leftIndent

        val result = mutableListOf<Block>()
        val runs = mutableListOf<TextRun>()

        fun flushRuns() {
            if (runs.isNotEmpty()) {
                result.add(Block.Para(runs.toList(), alignment, effectiveLeftIndent, rightIndent, firstLineIndent, spaceAbove, spaceBelow, lineSpacing))
                runs.clear()
            }
        }

        if (isNumbered) {
            val listId = node.getAttribute("ListId").ifEmpty { "default" }
            val n = (numberedListCounters[listId] ?: 0) + 1
            numberedListCounters[listId] = n
            runs.add(TextRun("$n. ", false, false, false, 11f, DEFAULT_COLOR))
        } else if (isBulleted) {
            runs.add(TextRun("• ", false, false, false, 11f, DEFAULT_COLOR))
        }

        forEachChildElement(node) { child ->
            when (child.tagName) {
                "content", "space" -> runs.add(extractRun(child, fullText))
                "image" -> {
                    flushRuns()
                    val base64Data = child.getAttribute("imageData")
                    val bitmap = decodeBase64Image(base64Data)
                    val w = child.getAttribute("width").toFloatOrNull() ?: bitmap?.width?.toFloat() ?: 100f
                    val h = child.getAttribute("height").toFloatOrNull() ?: bitmap?.height?.toFloat() ?: 100f
                    result.add(Block.Img(bitmap, w, h))
                }
            }
        }
        flushRuns()
        return result
    }

    private fun extractRun(node: Element, fullText: String): TextRun {
        val start = node.getAttribute("startOffset").toIntOrNull() ?: 0
        val length = node.getAttribute("length").toIntOrNull() ?: 0
        val safeStart = start.coerceIn(0, fullText.length)
        val safeEnd = (start + length).coerceIn(safeStart, fullText.length)
        val text = if (safeEnd > safeStart) fullText.substring(safeStart, safeEnd) else ""
        return TextRun(
            text = text,
            bold = node.getAttribute("bold") == "true",
            italic = node.getAttribute("italic") == "true",
            underline = node.getAttribute("underline") == "true",
            sizePt = node.attrFloat("size", 11f),
            colorArgb = node.getAttribute("foreground").toIntOrNull() ?: DEFAULT_COLOR,
            fontFamily = node.getAttribute("family").ifBlank { null }
        )
    }

    private fun parseTable(node: Element, fullText: String): Block.Tbl {
        val columnSpansAttr = node.getAttribute("columnSpans")
        var columnSpans = columnSpansAttr.split(",").mapNotNull { it.trim().toFloatOrNull() }
        val rows = mutableListOf<List<CellData>>()
        var maxCols = 0

        forEachChildElement(node) { rowNode ->
            if (rowNode.tagName == "row") {
                val cells = mutableListOf<CellData>()
                var rowCellCount = 0
                forEachChildElement(rowNode) { cellNode ->
                    if (cellNode.tagName == "cell") {
                        val colspan = cellNode.getAttribute("colspan").toIntOrNull() ?: 1
                        cells.add(CellData(colspan, parseBlocks(cellNode, fullText), cellNode.getAttribute("borderSpec").toIntOrNull() ?: 15, parseUdfColor(cellNode.getAttribute("fillColor")), cellNode.getAttribute("align")))
                        rowCellCount += colspan
                    }
                }
                rows.add(cells)
                if (rowCellCount > maxCols) maxCols = rowCellCount
            }
        }
        if (columnSpans.isEmpty() && maxCols > 0) columnSpans = List(maxCols) { 100f / maxCols }
        return Block.Tbl(columnSpans, rows)
    }

    private fun parseUdfColor(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return try {
            val v = value.trim().toLong()
            (v and 0xFFFFFFFFL).toInt().let { if (it == -1) null else it }
        } catch (_: Exception) { null }
    }

    // ==================================================================
    // Word -> PDF
    // ==================================================================

    fun convertWordToPdf(inputFile: File, outputFile: File): ConversionResult {
        return try {
            val blocks = mutableListOf<Block>()
            val extension = inputFile.extension.lowercase()

            if (extension == "docx") {
                FileInputStream(inputFile).use { fis ->
                    val docx = org.apache.poi.xwpf.usermodel.XWPFDocument(fis)
                    docx.bodyElements.forEach { element ->
                        if (element is org.apache.poi.xwpf.usermodel.XWPFParagraph) blocks.add(parseDocxParagraph(element))
                        else if (element is org.apache.poi.xwpf.usermodel.XWPFTable) blocks.add(parseDocxTable(element))
                    }
                }
            } else if (extension == "doc") {
                FileInputStream(inputFile).use { fis ->
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

    // --- Block Rendering Engine ---------------------------------------

    private fun renderBlocksToPdf(blocks: List<Block>, format: PageFormat, outputFile: File) {
        val pdfDocument = PdfDocument()
        val pageWidth = format.widthPt.toInt()
        val pageHeight = format.heightPt.toInt()
        val contentWidth = format.widthPt - format.leftMargin - format.rightMargin

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = format.topMargin

        for (block in blocks) {
            if (block is Block.PageBreak) {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = format.topMargin
                continue
            }
            val height = renderBlock(null, block, format.leftMargin, y, contentWidth)
            if (y + height > format.heightPt - format.bottomMargin && y > format.topMargin) {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = format.topMargin
            }
            renderBlock(canvas, block, format.leftMargin, y, contentWidth)
            y += height + 2f
        }
        pdfDocument.finishPage(page)
        writePdf(pdfDocument, outputFile)
        pdfDocument.close()
    }

    private fun renderBlock(canvas: Canvas?, block: Block, x: Float, y: Float, width: Float): Float {
        return when (block) {
            is Block.Para -> renderParagraph(canvas, block, x, y, width)
            is Block.Img -> renderImage(canvas, block, x, y, width)
            is Block.Tbl -> renderTable(canvas, block, x, y, width)
            Block.PageBreak -> 0f
        }
    }

    private fun renderParagraph(canvas: Canvas?, para: Block.Para, x: Float, y: Float, width: Float): Float {
        val ssb = SpannableStringBuilder()
        for (run in para.runs) {
            val start = ssb.length
            ssb.append(run.text)
            val end = ssb.length
            if (end > start) {
                ssb.setSpan(TypefaceSpan(when { run.bold && run.italic -> Typeface.BOLD_ITALIC; run.bold -> Typeface.BOLD; run.italic -> Typeface.ITALIC; else -> Typeface.NORMAL }.let { Typeface.create(Typeface.DEFAULT, it) }), start, end, 33)
                ssb.setSpan(AbsoluteSizeSpan(run.sizePt.toInt().coerceAtLeast(1), false), start, end, 33)
                ssb.setSpan(ForegroundColorSpan(run.colorArgb), start, end, 33)
            }
        }
        
        // Paragraf sonundaki gereksiz satır sonlarını temizleyelim (boşluk birikmesini önlemek için)
        while (ssb.isNotEmpty() && (ssb[ssb.length - 1] == '\n' || ssb[ssb.length - 1] == '\r')) {
            ssb.delete(ssb.length - 1, ssb.length)
        }

        if (ssb.isEmpty()) return 0f
        
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; density = 1.0f }
        val layout = StaticLayout.Builder.obtain(ssb, 0, ssb.length, paint, width.toInt().coerceAtLeast(1)).build()
        if (canvas != null) { canvas.save(); canvas.translate(x + para.leftIndent, y); layout.draw(canvas); canvas.restore() }
        return layout.height.toFloat()
    }

    private fun renderImage(canvas: Canvas?, img: Block.Img, x: Float, y: Float, width: Float): Float {
        val bitmap = img.bitmap ?: return 0f
        var w = img.widthPt; var h = img.heightPt
        if (w > width) { val s = width / w; w *= s; h *= s }
        if (canvas != null) canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), null)
        return h
    }

    private fun renderTable(canvas: Canvas?, table: Block.Tbl, x: Float, y: Float, width: Float): Float {
        val colWidths = table.columnSpans.map { it * (width / table.columnSpans.sum()) }
        var curY = y
        for (row in table.rows) {
            var rowH = 0f
            var curX = x
            for ((i, cell) in row.withIndex()) {
                val cw = colWidths[i]
                var ch = 0f
                for (b in cell.blocks) ch += renderBlock(null, b, 0f, 0f, cw - 10f) + 2f
                if (ch + 10f > rowH) rowH = ch + 10f
                if (canvas != null) {
                    canvas.drawRect(curX, curY, curX + cw, curY + rowH, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.5f })
                    var iy = curY + 5f
                    for (b in cell.blocks) iy += renderBlock(canvas, b, curX + 5f, iy, cw - 10f) + 2f
                }
                curX += cw
            }
            curY += rowH
        }
        return curY - y
    }

    private fun decodeBase64Image(base64: String): Bitmap? = try { Base64.decode(base64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (_: Exception) { null }
    private fun directChild(p: Element, t: String): Element? { var n = p.firstChild; while (n != null) { if (n is Element && n.tagName == t) return n; n = n.nextSibling }; return null }
    private fun forEachChildElement(p: Element, a: (Element) -> Unit) { var n = p.firstChild; while (n != null) { if (n is Element) a(n); n = n.nextSibling } }
    private fun Element?.attrFloat(n: String, d: Float): Float = this?.getAttribute(n)?.toFloatOrNull() ?: d
    private fun writePdf(d: PdfDocument, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { d.writeTo(it) } }

    /**
     * Merkezi paylaşım fonksiyonu. 
     * Dosya sağlayıcısı üzerinden URI oluşturur ve paylaşım intentini başlatır.
     */
    fun shareFile(context: android.content.Context, file: File, mimeType: String = "application/pdf") {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(com.avalibeyaz.evrak.R.string.share)))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Paylaşım başarısız oldu", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
