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
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TIFF ve UDF (UYAP Doküman Formatı) dosyalarını PDF'ye dönüştürür.
 *
 * UDF şeması https://github.com/saidsurucu/UDF-Toolkit/blob/main/Docs.md
 * dokümanına göre uygulanmıştır (format_id="1.8"):
 *  - UDF dosyası tek bir `content.xml` içeren bir ZIP arşividir.
 *  - Tüm düz metin (üstbilgi + gövde + altbilgi) `<content><![CDATA[...]]></content>`
 *    içinde TEK bir karakter havuzunda tutulur.
 *  - `<elements>` altındaki paragraf/tablo/resim elemanları bu havuzu
 *    `startOffset`/`length` (karakter/rune bazlı, byte değil) ile referans alır.
 *  - Resimler ZIP içinde değil, `<image imageData="[base64]" .../>` olarak
 *    doğrudan XML içine gömülüdür.
 *
 * Bağımlılıklar (build.gradle.kts -> app modülü):
 *
 *   // Çok sayfalı TIFF decode için (Android'in kendi BitmapFactory'si TIFF desteklemiyor)
 *   implementation("com.github.beyka:TiffBitmapFactory:0.9.9")
 *   // JitPack repo'sunu settings.gradle.kts / build.gradle.kts'e eklemeyi unutmayın:
 *   // maven { url = uri("https://jitpack.io") }
 */
object DocumentConverter {

    private const val TAG = "DocumentConverter"

    sealed class ConversionResult {
        data class Success(val outputFile: File) : ConversionResult()
        data class Error(val message: String, val cause: Throwable? = null) : ConversionResult()
    }

    /** Dosya uzantısına bakarak uygun dönüştürücüyü seçer. */
    fun convert(inputFile: File, outputFile: File): ConversionResult {
        if (!inputFile.exists()) {
            return ConversionResult.Error("Girdi dosyası bulunamadı: ${inputFile.absolutePath}")
        }
        return when (inputFile.extension.lowercase()) {
            "tif", "tiff" -> convertTiffToPdf(inputFile, outputFile)
            "udf" -> convertUdfToPdf(inputFile, outputFile)
            else -> ConversionResult.Error("Desteklenmeyen dosya türü: .${inputFile.extension}")
        }
    }

    // ==================================================================
    // TIFF -> PDF
    // ==================================================================

    /**
     * Çok sayfalı veya tek sayfalı TIFF dosyasını PDF'ye dönüştürür.
     * Her TIFF sayfası, boyutuna uygun bir PDF sayfasına tam olarak çizilir.
     */
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
    // UDF -> PDF
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

    private const val CELL_PADDING = 5.4f
    private const val DEFAULT_COLOR = -16777216 // siyah, işaretli ARGB

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

            val root = doc.documentElement // <template format_id="1.8">
                ?: return ConversionResult.Error("Geçersiz UDF: kök eleman bulunamadı")

            val fullText = directChild(root, "content")?.textContent
                ?: return ConversionResult.Error("Geçersiz UDF: <content> havuzu bulunamadı")

            val pageFormatNode = directChild(root, "properties")?.let { directChild(it, "pageFormat") }
            val pageFormat = PageFormat(
                widthPt = 595.28f,
                heightPt = 841.89f,
                leftMargin = pageFormatNode.attrFloat("leftMargin", 42.52f),
                rightMargin = pageFormatNode.attrFloat("rightMargin", 28.35f),
                topMargin = pageFormatNode.attrFloat("topMargin", 14.17f),
                bottomMargin = pageFormatNode.attrFloat("bottomMargin", 14.17f)
            )

            val elementsNode = directChild(root, "elements")
                ?: return ConversionResult.Error("Geçersiz UDF: <elements> bölümü bulunamadı")

            // Üstbilgi + gövde + altbilgi aynı akışta, karşılaşılma sırasıyla işlenir.
            // (Üstbilginin/altbilginin her sayfada tekrarlanması bu basit render'da desteklenmiyor.)
            val blocks = parseBlocks(elementsNode, fullText)

            renderUdfToPdf(blocks, pageFormat, outputFile)
            ConversionResult.Success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "UDF -> PDF dönüştürme hatası", e)
            ConversionResult.Error("UDF dönüştürme hatası: ${e.message}", e)
        }
    }

    // --- XML -> Block ağacı -------------------------------------------

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
        
        val effectiveLeftIndent = if (isNumbered || isBulleted) {
            leftIndent + (listLevel + 1) * 18f
        } else {
            leftIndent
        }

        val result = mutableListOf<Block>()
        val runs = mutableListOf<TextRun>()

        fun flushRuns() {
            if (runs.isNotEmpty()) {
                result.add(
                    Block.Para(
                        runs.toList(), alignment, effectiveLeftIndent, rightIndent,
                        firstLineIndent, spaceAbove, spaceBelow, lineSpacing
                    )
                )
                runs.clear()
            }
        }

        // Liste işareti (prefix) ekle
        if (isNumbered) {
            val listId = node.getAttribute("ListId").ifEmpty { "default" }
            val n = (numberedListCounters[listId] ?: 0) + 1
            numberedListCounters[listId] = n
            val marker = numberMarker(n, node.getAttribute("NumberType")) + " "
            runs.add(
                TextRun(
                    marker, bold = false, italic = false, underline = false,
                    sizePt = node.attrFloat("size", 11f), colorArgb = DEFAULT_COLOR
                )
            )
        } else if (isBulleted) {
            val marker = bulletMarker(node.getAttribute("BulletType")) + " "
            runs.add(
                TextRun(
                    marker, bold = false, italic = false, underline = false,
                    sizePt = node.attrFloat("size", 11f), colorArgb = DEFAULT_COLOR
                )
            )
        }

        forEachChildElement(node) { child ->
            when (child.tagName) {
                "content", "space" -> runs.add(extractRun(child, fullText))
                "field" -> {
                    var run = extractRun(child, fullText)
                    if (run.text.isEmpty() || run.text == "\u200B") {
                        val name = child.getAttribute("fieldName").ifEmpty { child.getAttribute("name") }
                        val default = child.getAttribute("default").ifEmpty { if (name.isNotEmpty()) "[$name]" else "" }
                        if (default.isNotEmpty()) {
                            run = run.copy(text = default)
                        }
                    }
                    runs.add(run)
                }
                "tab" -> runs.add(
                    TextRun(
                        "\t", bold = false, italic = false, underline = false,
                        sizePt = child.attrFloat("size", 11f), colorArgb = DEFAULT_COLOR
                    )
                )
                "image" -> {
                    flushRuns()
                    val base64Data = child.getAttribute("imageData")
                    val bitmap = decodeBase64Image(base64Data)
                    val w = child.attrFloat("width", 100f)
                    val h = child.attrFloat("height", 100f)
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

    // --- Liste İşaretçileri ---

    private fun numberMarker(n: Int, type: String?): String {
        return when (type) {
            "1", "decimal" -> "$n."
            "a" -> "${toAlpha(n, false)}."
            "A" -> "${toAlpha(n, true)}."
            "i" -> "${toRoman(n).lowercase()}."
            "I" -> "${toRoman(n)}."
            else -> "$n."
        }
    }

    private fun bulletMarker(type: String?): String {
        return when (type) {
            "disc" -> "●"
            "circle" -> "○"
            "square" -> "■"
            else -> "•"
        }
    }

    private fun toAlpha(n: Int, upper: Boolean): String {
        var num = n - 1
        val res = StringBuilder()
        while (num >= 0) {
            res.insert(0, ('A'.code + (num % 26)).toChar())
            num = num / 26 - 1
        }
        return if (upper) res.toString() else res.toString().lowercase()
    }

    private fun toRoman(n: Int): String {
        val map = mapOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var num = n
        val res = StringBuilder()
        for ((v, s) in map) {
            while (num >= v) {
                res.append(s)
                num -= v
            }
        }
        return res.toString()
    }

    private fun parseTable(node: Element, fullText: String): Block.Tbl {
        val columnSpans = node.getAttribute("columnSpans")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
        val rows = mutableListOf<List<CellData>>()

        forEachChildElement(node) { rowNode ->
            if (rowNode.tagName == "row") {
                val cells = mutableListOf<CellData>()
                forEachChildElement(rowNode) { cellNode ->
                    if (cellNode.tagName == "cell") {
                        val colspan = cellNode.getAttribute("colspan").toIntOrNull() ?: 1
                        val borderSpec = cellNode.getAttribute("borderSpec").toIntOrNull() ?: 15
                        val fillColorStr = cellNode.getAttribute("fillColor")
                        val fillColor = parseUdfColor(fillColorStr)
                        val vAlign = cellNode.getAttribute("align") // vcenter, bottom
                        
                        cells.add(
                            CellData(
                                colspan = colspan,
                                blocks = parseBlocks(cellNode, fullText),
                                borderSpec = borderSpec,
                                fillColor = fillColor,
                                verticalAlign = vAlign
                            )
                        )
                    }
                }
                rows.add(cells)
            }
        }
        return Block.Tbl(columnSpans, rows)
    }

    private fun parseUdfColor(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return try {
            val v = value.trim().toLong()
            val argb = (v and 0xFFFFFFFFL).toInt()
            // UDF renkleri bazen şeffaf (255,255,255 gibi) geliyor, 
            // beyazı şeffaf kabul etmek gerekebilir ama burada doğrudan ARGB dönüyoruz.
            if (argb == -1) null else argb 
        } catch (_: Exception) {
            null
        }
    }

    // --- Block ağacı -> PDF ---------------------------------------------

    private fun renderUdfToPdf(blocks: List<Block>, format: PageFormat, outputFile: File) {
        val pdfDocument = PdfDocument()
        val pageWidth = format.widthPt.toInt()
        val pageHeight = format.heightPt.toInt()
        val contentWidth = format.widthPt - format.leftMargin - format.rightMargin

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = format.topMargin

        fun newPage() {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = format.topMargin
        }

        for (block in blocks) {
            if (block is Block.PageBreak) {
                newPage()
                continue
            }
            // Önce yükseklik ölç (canvas=null -> sadece ölçüm), sığmıyorsa yeni sayfa aç.
            val spaceAbove = if (block is Block.Para) block.spaceAbove else 0f
            val spaceBelow = if (block is Block.Para) block.spaceBelow else 0f
            
            val measuredHeight = renderBlock(null, block, format.leftMargin, y + spaceAbove, contentWidth)
            if (y + spaceAbove + measuredHeight + spaceBelow > format.heightPt - format.bottomMargin && y > format.topMargin) {
                newPage()
            }
            val drawnHeight = renderBlock(canvas, block, format.leftMargin, y + spaceAbove, contentWidth)
            y += spaceAbove + drawnHeight + spaceBelow + 2f
        }

        pdfDocument.finishPage(page)
        writePdf(pdfDocument, outputFile)
        pdfDocument.close()
    }

    /** canvas == null ise sadece yükseklik hesaplar, hiçbir şey çizmez (ölçüm geçişi). */
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
        val flag = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        for (run in para.runs) {
            val start = ssb.length
            ssb.append(run.text)
            val end = ssb.length
            if (end == start) continue
            
            // Font seçimi
            val tf = getUdfTypeface(run.fontFamily, run.bold, run.italic)
            ssb.setSpan(TypefaceSpan(tf), start, end, flag)
            
            val style = when {
                run.bold && run.italic -> Typeface.BOLD_ITALIC
                run.bold -> Typeface.BOLD
                run.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) ssb.setSpan(StyleSpan(style), start, end, flag)
            ssb.setSpan(AbsoluteSizeSpan(run.sizePt.toInt().coerceAtLeast(1), false), start, end, flag)
            ssb.setSpan(ForegroundColorSpan(run.colorArgb), start, end, flag)
            if (run.underline) ssb.setSpan(UnderlineSpan(), start, end, flag)
        }
        if (ssb.isEmpty()) return 0f

        // Trim trailing newlines to avoid extra blank line at the end of every paragraph
        while (ssb.isNotEmpty() && (ssb[ssb.length - 1] == '\n' || ssb[ssb.length - 1] == '\r')) {
            ssb.delete(ssb.length - 1, ssb.length)
        }
        if (ssb.isEmpty()) return 0f

        // First Line Indent (Birinci satır girintisi)
        if (para.firstLineIndent != 0f) {
            ssb.setSpan(
                LeadingMarginSpan.Standard(para.firstLineIndent.toInt(), 0),
                0, ssb.length, flag
            )
        }

        val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.BLACK
        }
        
        val alignment = when (para.alignment) {
            1 -> Layout.Alignment.ALIGN_CENTER
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        
        // Justification (İki yana yasla - Alignment 3)
        val justification = if (para.alignment == 3) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }

        val effectiveWidth = (width - para.leftIndent - para.rightIndent).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(ssb, 0, ssb.length, basePaint, effectiveWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.0f + para.lineSpacing)
            .setJustificationMode(justification)
            .build()

        if (canvas != null) {
            canvas.save()
            // Girintileri uygula. firstLineIndent sadece ilk satırda uygulanır.
            // StaticLayout'ta textIndent özelliği doğrudan olmadığı için manuel kaydırma veya 
            // LeadingMarginSpan gerekebilir ama basitlik için x koordinatını kaydırıyoruz.
            canvas.translate(x + para.leftIndent, y)
            layout.draw(canvas)
            canvas.restore()
        }
        return layout.height.toFloat()
    }

    private fun getUdfTypeface(family: String?, bold: Boolean, italic: Boolean): Typeface {
        val base = when (family?.lowercase()) {
            "arial" -> Typeface.SANS_SERIF
            "courier new" -> Typeface.MONOSPACE
            "times new roman", "serif" -> Typeface.SERIF
            else -> Typeface.DEFAULT
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    private fun renderImage(canvas: Canvas?, img: Block.Img, x: Float, y: Float, width: Float): Float {
        val bitmap = img.bitmap ?: return 0f
        var w = img.widthPt
        var h = img.heightPt
        if (w > width && w > 0f) {
            val scale = width / w
            w *= scale
            h *= scale
        }
        if (canvas != null) {
            canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), null)
        }
        return h
    }

    private fun renderTable(canvas: Canvas?, table: Block.Tbl, x: Float, y: Float, width: Float): Float {
        val totalSpec = table.columnSpans.sum()
        val scale = if (totalSpec > 0f) width / totalSpec else 1f
        val colWidths = table.columnSpans.map { it * scale }
        
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Color.BLACK
        }
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        var curY = y
        for (row in table.rows) {
            var colIndex = 0
            val cellWidths = row.map { cell ->
                val w = (0 until cell.colspan).sumOf { i -> (colWidths.getOrNull(colIndex + i) ?: 0f).toDouble() }.toFloat()
                colIndex += cell.colspan
                w
            }
            
            // 1. Ölçüm geçişi: Satır yüksekliğini belirle
            val cellContentHeights = row.mapIndexed { i, cell ->
                val innerWidth = (cellWidths[i] - CELL_PADDING * 2).coerceAtLeast(1f)
                var h = 0f
                for (b in cell.blocks) h += renderBlock(null, b, 0f, 0f, innerWidth) + 2f
                h
            }
            val rowHeight = (cellContentHeights.maxOrNull() ?: 0f) + CELL_PADDING * 2

            // 2. Çizim geçişi
            var curX = x
            for ((i, cell) in row.withIndex()) {
                val cellWidth = cellWidths[i]
                
                if (canvas != null) {
                    // Arka plan rengi
                    cell.fillColor?.let {
                        fillPaint.color = it
                        canvas.drawRect(curX, curY, curX + cellWidth, curY + rowHeight, fillPaint)
                    }
                    
                    // Kenarlıklar (borderSpec maskesine göre)
                    // 1:top, 2:right, 4:bottom, 8:left
                    if (cell.borderSpec and 1 != 0) canvas.drawLine(curX, curY, curX + cellWidth, curY, borderPaint)
                    if (cell.borderSpec and 2 != 0) canvas.drawLine(curX + cellWidth, curY, curX + cellWidth, curY + rowHeight, borderPaint)
                    if (cell.borderSpec and 4 != 0) canvas.drawLine(curX, curY + rowHeight, curX + cellWidth, curY + rowHeight, borderPaint)
                    if (cell.borderSpec and 8 != 0) canvas.drawLine(curX, curY, curX, curY + rowHeight, borderPaint)
                    
                    // İçerik başlangıç Y pozisyonu (Dikey hizalama)
                    val contentHeight = cellContentHeights[i]
                    var innerY = when (cell.verticalAlign) {
                        "vcenter" -> curY + (rowHeight - contentHeight) / 2f
                        "bottom" -> curY + rowHeight - contentHeight - CELL_PADDING
                        else -> curY + CELL_PADDING
                    }
                    
                    val innerWidth = (cellWidth - CELL_PADDING * 2).coerceAtLeast(1f)
                    for (b in cell.blocks) {
                        val h = renderBlock(canvas, b, curX + CELL_PADDING, innerY, innerWidth)
                        innerY += h + 2f
                    }
                }
                curX += cellWidth
            }
            curY += rowHeight
        }
        return curY - y
    }

    // --- Yardımcılar -----------------------------------------------------

    private fun decodeBase64Image(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Resim decode edilemedi", e)
            null
        }
    }

    private fun directChild(parent: Element, tagName: String): Element? {
        var node: Node? = parent.firstChild
        while (node != null) {
            if (node is Element && node.tagName == tagName) return node
            node = node.nextSibling
        }
        return null
    }

    private inline fun forEachChildElement(parent: Element, action: (Element) -> Unit) {
        var node: Node? = parent.firstChild
        while (node != null) {
            if (node is Element) action(node)
            node = node.nextSibling
        }
    }

    private fun Element?.attrFloat(name: String, default: Float): Float {
        if (this == null) return default
        val v = getAttribute(name)
        return v.toFloatOrNull() ?: default
    }

    private fun writePdf(pdfDocument: PdfDocument, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
    }
}