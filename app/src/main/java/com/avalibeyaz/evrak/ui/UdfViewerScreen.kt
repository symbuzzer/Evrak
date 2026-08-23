package com.avalibeyaz.evrak.ui

import android.webkit.WebView
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Viewer for UYAP UDF (Ulusal Yargı Ağı Projesi Doküman Formatı) files.
 *
 * A .udf file is a plain ZIP archive containing a single "content.xml" that holds:
 *  - <content>       a single CDATA text pool for the whole document (header+body+footer)
 *  - <properties>    page format (margins, size, orientation) + optional background image
 *  - <elements>      the document structure (header/paragraph/table/page-break/footer),
 *                     whose runs reference the text pool via startOffset/length
 *  - <styles>        named style definitions (font family/size/color) used as fallbacks
 *
 * We parse this into HTML and render it inside a WebView, mirroring the approach already
 * used for .doc/.docx in WordViewerScreen.kt.
 *
 * Reference: https://github.com/saidsurucu/UDF-Toolkit/blob/main/Docs.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdfViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConverting by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            htmlContent = try {
                val file = File(filePath)
                if (!file.exists()) {
                    val errorTitleStr = context.getString(R.string.error)
                    val errorNotFoundStr = context.getString(R.string.error_file_not_found)
                    wrapUdfHtml("<div class='error'><h3>$errorTitleStr</h3><p>$errorNotFoundStr</p></div>")
                } else {
                    val xml = readUdfContentXml(file)
                    val errorTitleStr = context.getString(R.string.error)
                    val errorReadStr = context.getString(R.string.error_udf_read)
                    
                    if (xml.isNullOrBlank()) {
                        wrapUdfHtml("<div class='error'><h3>$errorTitleStr</h3><p>$errorReadStr</p></div>")
                    } else {
                        val body = UdfDocumentParser(xml).parseToHtmlBody()
                        wrapUdfHtml(
                            body.html,
                            body.pageWidthPt,
                            body.pageHeightPt,
                            body.topMargin,
                            body.bottomMargin,
                            body.leftMargin,
                            body.rightMargin
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorTitle = context.getString(R.string.error_file_open_failed)
                val errorMsg = e.localizedMessage ?: context.getString(R.string.error_unknown)
                wrapUdfHtml(
                    """
                    <div class='error'>
                        <h3>$errorTitle</h3>
                        <p><strong>${e.javaClass.simpleName}</strong></p>
                        <p>$errorMsg</p>
                    </div>
                    """.trimIndent()
                )
            } finally {
                isLoading = false
            }
        }
    }

    // Save launchers
    val saveUdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        File(filePath).inputStream().use { input -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            scope.launch(Dispatchers.IO) {
                isConverting = true
                try {
                    val tempPdf = File(context.cacheDir, "temp_udf_convert.pdf")
                    val result = DocumentConverter.convertUdfToPdf(File(filePath), tempPdf)
                    if (result is DocumentConverter.ConversionResult.Success) {
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            tempPdf.inputStream().use { input -> input.copyTo(output) }
                        }
                    } else if (result is DocumentConverter.ConversionResult.Error) {
                        withContext(Dispatchers.Main) {
                            loadError = context.getString(R.string.error_conversion_failed, result.message)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isConverting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { MarqueeTitle(title = displayName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showFormatDialog = "save" }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                    }
                    IconButton(onClick = { showFormatDialog = "share" }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.javaScriptEnabled = false
                            setBackgroundColor(android.graphics.Color.WHITE)
                        }
                    },
                    update = { webView ->
                        htmlContent?.let {
                            webView.loadDataWithBaseURL(null, it, "text/html", "UTF-8", null)
                        }
                    }
                )
            }

            if (isConverting) {
                Box(
                    modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(id = R.string.converting), color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            
            loadError?.let { error ->
                AlertDialog(
                    onDismissRequest = { loadError = null },
                    title = { Text(text = stringResource(id = R.string.error)) },
                    text = { Text(text = error) },
                    confirmButton = {
                        TextButton(onClick = { loadError = null }) {
                            Text(text = stringResource(id = R.string.ok))
                        }
                    }
                )
            }
        }
    }

    if (showFormatDialog != null) {
        FormatSelectionDialog(
            extension = "UDF",
            onDismiss = { showFormatDialog = null },
            onFormatSelected = { usePdf ->
                if (showFormatDialog == "save") {
                    if (usePdf) {
                        val newName = displayName.substringBeforeLast(".") + ".pdf"
                        savePdfLauncher.launch(newName)
                    } else {
                        saveUdfLauncher.launch(displayName)
                    }
                } else {
                    // share
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            isConverting = true
                            try {
                                val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convertUdfToPdf(File(filePath), tempPdf)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    shareConvertedFile(context, tempPdf, "application/pdf")
                                }
                            } finally {
                                isConverting = false
                            }
                        } else {
                            onShareClick()
                        }
                    }
                }
            }
        )
    }
}

private fun shareConvertedFile(context: android.content.Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}

// region --- ZIP / XML extraction ---

private fun readUdfContentXml(file: File): String? {
    ZipFile(file).use { zip ->
        val entry = zip.entries().asSequence().firstOrNull {
            !it.isDirectory && it.name.substringAfterLast('/').equals("content.xml", ignoreCase = true)
        } ?: return null
        zip.getInputStream(entry).use { stream ->
            return stream.readBytes().toString(Charsets.UTF_8)
        }
    }
}

// endregion

// region --- HTML shell ---

private fun wrapUdfHtml(
    body: String,
    pageWidthPt: Double = 595.28,
    pageHeightPt: Double = 841.89,
    topMargin: Double = 56.7,
    bottomMargin: Double = 56.7,
    leftMargin: Double = 56.7,
    rightMargin: Double = 56.7
): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=${pageWidthPt.toInt()}, user-scalable=yes">
            <style>
                html { height: 100%; }
                body {
                    min-height: 100%;
                    margin: 0;
                    padding: 0 0 40px 0;
                    background-color: #E9E9E9;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    font-family: 'Times New Roman', serif;
                }
                .udf-page {
                    background-color: #ffffff;
                    width: ${pageWidthPt}pt;
                    min-height: ${pageHeightPt}pt;
                    margin: 16px auto;
                    padding: ${topMargin}pt ${rightMargin}pt ${bottomMargin}pt ${leftMargin}pt;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                    box-sizing: border-box;
                    color: #000000;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    flex-shrink: 0;
                }
                .udf-header {
                    border-bottom: 1px solid #cccccc;
                    padding-bottom: 6pt;
                    margin-bottom: 10pt;
                }
                .udf-footer {
                    border-top: 1px solid #cccccc;
                    padding-top: 6pt;
                    margin-top: 10pt;
                    font-size: 9pt;
                    color: #555555;
                }
                .udf-paragraph { margin: 0; padding: 0; min-height: 1em; }
                .udf-page-break { border-top: 1px dashed #bbbbbb; margin: 24pt 0; }
                table.udf-table { border-collapse: collapse; margin: 8pt 0; width: 100%; table-layout: fixed; }
                table.udf-table td { padding: 5.4pt; vertical-align: top; word-break: break-word; }
                .udf-tab { display: inline-block; min-width: 36pt; }
                img.udf-image { max-width: 100%; height: auto; }
                .error { text-align: center; padding: 60px; color: #e74c3c; }
                .info { text-align: center; padding: 60px; color: #7f8c8d; }
            </style>
        </head>
        <body>
            <div class="udf-page">
                $body
            </div>
        </body>
        </html>
    """.trimIndent()
}

// endregion

private data class UdfBody(
    val html: String,
    val pageWidthPt: Double,
    val pageHeightPt: Double,
    val topMargin: Double,
    val bottomMargin: Double,
    val leftMargin: Double,
    val rightMargin: Double
)

private data class UdfStyle(
    val family: String? = null,
    val size: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val foreground: String? = null
)

/**
 * Parses a UDF content.xml document into an HTML fragment.
 */
private class UdfDocumentParser(private val xml: String) {

    private var contentPool: String = ""
    private var resolverStyleName: String = "default"
    private val styles = mutableMapOf<String, UdfStyle>()
    private val numberedListCounters = mutableMapOf<String, Int>()

    fun parseToHtmlBody(): UdfBody {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Basic XXE hardening - UDF is a local user-provided file.
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xml.byteInputStream(Charsets.UTF_8))
        val root = doc.documentElement ?: return UdfBody("<div class='error'><p>Invalid content.</p></div>", 595.28, 841.89, 56.7, 56.7, 56.7, 56.7)

        var pageWidthPt = 595.28
        var pageHeightPt = 841.89
        var topMargin = 56.7
        var bottomMargin = 56.7
        var leftMargin = 56.7
        var rightMargin = 56.7

        var contentEl: Element? = null
        var propertiesEl: Element? = null
        var elementsEl: Element? = null
        var stylesEl: Element? = null

        forEachChildElement(root) { child ->
            when (child.tagName) {
                "content" -> contentEl = child
                "properties" -> propertiesEl = child
                "elements" -> elementsEl = child
                "styles" -> stylesEl = child
            }
        }

        contentPool = contentEl?.textContent ?: ""

        stylesEl?.let { parseStyles(it) }

        propertiesEl?.let { props ->
            forEachChildElement(props) { child ->
                if (child.tagName == "pageFormat") {
                    val orientation = child.attrOrNull("paperOrientation")
                    val w = 595.28
                    val h = 841.89
                    
                    if (orientation == "0" || orientation == "2") { // Landscape
                        pageWidthPt = h
                        pageHeightPt = w
                    } else { // Portrait
                        pageWidthPt = w
                        pageHeightPt = h
                    }

                    child.attrOrNull("topMargin")?.toDoubleOrNull()?.let { topMargin = it }
                    child.attrOrNull("bottomMargin")?.toDoubleOrNull()?.let { bottomMargin = it }
                    child.attrOrNull("leftMargin")?.toDoubleOrNull()?.let { leftMargin = it }
                    child.attrOrNull("rightMargin")?.toDoubleOrNull()?.let { rightMargin = it }
                }
            }
        }

        val elementsSb = StringBuilder()
        elementsEl?.let { elements ->
            resolverStyleName = elements.attrOrNull("resolver") ?: "default"
            forEachChildElement(elements) { child ->
                elementsSb.append(renderStructuralElement(child))
            }
        }

        if (elementsSb.isEmpty()) {
            elementsSb.append("<div class='info'><p>No content found.</p></div>")
        }

        return UdfBody(elementsSb.toString(), pageWidthPt, pageHeightPt, topMargin, bottomMargin, leftMargin, rightMargin)
    }

    private fun parseStyles(stylesEl: Element) {
        forEachChildElement(stylesEl) { styleEl ->
            if (styleEl.tagName != "style") return@forEachChildElement
            val name = styleEl.attrOrNull("name") ?: return@forEachChildElement
            styles[name] = UdfStyle(
                family = styleEl.attrOrNull("family"),
                size = styleEl.attrOrNull("size"),
                bold = styleEl.attrOrNull("bold")?.toBoolean() ?: false,
                italic = styleEl.attrOrNull("italic")?.toBoolean() ?: false,
                foreground = colorToHex(styleEl.attrOrNull("foreground"))
            )
        }
    }

    private fun defaultStyle(): UdfStyle =
        styles[resolverStyleName] ?: styles["default"] ?: UdfStyle(
            family = "Times New Roman",
            size = "11",
            foreground = "#000000"
        )

    // --- structural elements: header / footer / paragraph / table / page-break ---

    private fun renderStructuralElement(el: Element): String {
        return try {
            when (el.tagName) {
                "header" -> "<div class=\"udf-header\">${renderContainerChildren(el)}</div>"
                "footer" -> "<div class=\"udf-footer\">${renderContainerChildren(el)}</div>"
                "paragraph" -> renderParagraph(el)
                "table" -> renderTable(el)
                "page-break" -> {
                    val inner = renderContainerChildren(el)
                    "<div class=\"udf-page-break\"></div>$inner"
                }
                else -> ""
            }
        } catch (e: Exception) {
            // Never let a single malformed element take down the whole render.
            ""
        }
    }

    private fun renderContainerChildren(el: Element): String {
        val sb = StringBuilder()
        forEachChildElement(el) { child ->
            sb.append(renderStructuralElement(child))
        }
        return sb.toString()
    }

    // --- paragraph ---

    private fun renderParagraph(p: Element): String {
        val style = StringBuilder()

        when (p.attrOrNull("Alignment")) {
            "1" -> style.append("text-align:center;")
            "2" -> style.append("text-align:right;")
            "3" -> style.append("text-align:justify;")
            else -> style.append("text-align:left;")
        }

        p.attrOrNull("LeftIndent")?.toDoubleOrNull()?.let { style.append("margin-left:${it}pt;") }
        p.attrOrNull("RightIndent")?.toDoubleOrNull()?.let { style.append("margin-right:${it}pt;") }
        p.attrOrNull("FirstLineIndent")?.toDoubleOrNull()?.let { style.append("text-indent:${it}pt;") }
        p.attrOrNull("SpaceAbove")?.toDoubleOrNull()?.let { style.append("margin-top:${it}pt;") }
        p.attrOrNull("SpaceBelow")?.toDoubleOrNull()?.let { style.append("margin-bottom:${it}pt;") }
        p.attrOrNull("LineSpacing")?.toDoubleOrNull()?.let { style.append("line-height:${1.0 + it};") }

        val isNumbered = p.attrOrNull("Numbered")?.toBoolean() == true
        val isBulleted = p.attrOrNull("Bulleted")?.toBoolean() == true
        val listLevel = p.attrOrNull("ListLevel")?.toIntOrNull() ?: 0
        if (isNumbered || isBulleted) {
            style.append("margin-left:${(listLevel + 1) * 18}pt;")
        }

        val prefix = when {
            isNumbered -> {
                val listId = p.attrOrNull("ListId") ?: "default"
                val n = (numberedListCounters[listId] ?: 0) + 1
                numberedListCounters[listId] = n
                htmlEscape(numberMarker(n, p.attrOrNull("NumberType"))) + "&nbsp;"
            }
            isBulleted -> htmlEscape(bulletMarker(p.attrOrNull("BulletType"))) + "&nbsp;"
            else -> ""
        }

        val inner = StringBuilder(prefix)
        forEachChildElement(p) { child ->
            inner.append(renderInlineElement(child))
        }

        return "<div class=\"udf-paragraph\" style=\"$style\">$inner</div>"
    }

    private fun numberMarker(n: Int, type: String?): String = when (type) {
        "NUMBER_TYPE_NUMBER_PARENTHESIS" -> "$n)"
        "NUMBER_TYPE_CHAR_SMALL_DOT" -> "${toAlpha(n, false)}."
        "NUMBER_TYPE_CHAR_SMALL_PARENTHESIS" -> "${toAlpha(n, false)})"
        "NUMBER_TYPE_CHAR_BIG_DOT" -> "${toAlpha(n, true)}."
        "NUMBER_TYPE_CHAR_BIG_PARENTHESIS" -> "${toAlpha(n, true)})"
        "NUMBER_TYPE_ROMAN_SMALL_DOT" -> "${toRoman(n).lowercase()}."
        "NUMBER_TYPE_ROMAN_SMALL_PARENTHESIS" -> "${toRoman(n).lowercase()})"
        "NUMBER_TYPE_ROMAN_BIG_DOT" -> "${toRoman(n)}."
        "NUMBER_TYPE_ROMAN_BIG_PARENTHESIS" -> "${toRoman(n)})"
        else -> "$n." // NUMBER_TYPE_NUMBER_DOT default
    }

    private fun bulletMarker(type: String?): String = when (type) {
        "BULLET_TYPE_RECTANGLE" -> "\u25A0"
        "BULLET_TYPE_ARROW" -> "\u27A4"
        "BULLET_TYPE_DIAMOND" -> "\u25C6"
        "BULLET_TYPE_DIAMOND_2" -> "\u25CA"
        "BULLET_TYPE_TRIANGLE" -> "\u25B2"
        "BULLET_TYPE_RECTANGLE_D" -> "\u25A1"
        else -> "\u2022" // BULLET_TYPE_ELLIPSE default
    }

    private fun toAlpha(n: Int, upper: Boolean): String {
        var num = n
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.insert(0, ('a' + rem))
            num = (num - 1) / 26
        }
        return if (upper) sb.toString().uppercase() else sb.toString()
    }

    private fun toRoman(n: Int): String {
        if (n <= 0) return n.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var num = n
        val sb = StringBuilder()
        for (i in values.indices) {
            while (num >= values[i]) {
                num -= values[i]
                sb.append(symbols[i])
            }
        }
        return sb.toString()
    }

    // --- inline elements: content / image / tab / space / field ---

    private fun renderInlineElement(el: Element): String {
        return try {
            when (el.tagName) {
                "content" -> renderContentRun(el)
                "image" -> renderImage(el)
                "tab" -> "<span class=\"udf-tab\">&nbsp;</span>"
                "space" -> htmlEscape(extractText(el)).ifEmpty { "&nbsp;" }
                "field" -> {
                    // Placeholder for template fields not filled by a <data> section.
                    val name = el.attrOrNull("name") ?: el.attrOrNull("fieldName") ?: ""
                    val default = el.attrOrNull("default") ?: "[$name]"
                    htmlEscape(default)
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun renderContentRun(el: Element): String {
        val text = extractText(el)
        // Zero-width space marks an intentionally empty paragraph - nothing visible to render.
        if (text.isEmpty() || text == "\u200B") return ""

        val runStyle = StringBuilder()
        val family = el.attrOrNull("family") ?: defaultStyle().family
        val size = el.attrOrNull("size") ?: defaultStyle().size
        family?.let { runStyle.append("font-family:'$it';") }
        size?.toDoubleOrNull()?.let { runStyle.append("font-size:${it}pt;") }
        if (el.attrOrNull("bold")?.toBoolean() == true) runStyle.append("font-weight:bold;")
        if (el.attrOrNull("italic")?.toBoolean() == true) runStyle.append("font-style:italic;")
        if (el.attrOrNull("underline")?.toBoolean() == true) runStyle.append("text-decoration:underline;")
        (colorToHex(el.attrOrNull("foreground")) ?: defaultStyle().foreground)?.let {
            runStyle.append("color:$it;")
        }
        colorToHex(el.attrOrNull("background"))?.let {
            if (it != "#FFFFFF") runStyle.append("background-color:$it;")
        }

        val escaped = htmlEscape(text).replace("\n", "<br/>")
        return "<span style=\"$runStyle\">$escaped</span>"
    }

    private fun renderImage(el: Element): String {
        val data = el.attrOrNull("imageData") ?: return ""
        val width = el.attrOrNull("width")?.toDoubleOrNull()
        val height = el.attrOrNull("height")?.toDoubleOrNull()
        val style = StringBuilder()
        width?.let { style.append("width:${it}pt;") }
        height?.let { style.append("height:${it}pt;") }
        // imageData is already base64; UDF stores JPEG or PNG, so a generic data URL prefix works
        // reasonably well for either (browsers sniff the actual bytes if the tag is wrong).
        return "<img class=\"udf-image\" style=\"$style\" src=\"data:image/*;base64,$data\" />"
    }

    // --- table ---

    private fun renderTable(table: Element): String {
        val columnSpans = table.attrOrNull("columnSpans")
            ?.split(",")
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            ?: emptyList()

        val colGroup = if (columnSpans.isNotEmpty()) {
            val total = columnSpans.sum().takeIf { it > 0 } ?: 1.0
            buildString {
                append("<colgroup>")
                columnSpans.forEach { span ->
                    val pct = (span / total) * 100.0
                    append("<col style=\"width:${pct}%;\"/>")
                }
                append("</colgroup>")
            }
        } else ""

        val rowsHtml = StringBuilder()
        forEachChildElement(table) { rowEl ->
            if (rowEl.tagName == "row") {
                rowsHtml.append(renderRow(rowEl))
            }
        }

        return "<table class=\"udf-table\">$colGroup<tbody>$rowsHtml</tbody></table>"
    }

    private fun renderRow(row: Element): String {
        val rowStyle = StringBuilder()
        row.attrOrNull("height")?.toDoubleOrNull()?.let {
            if (it > 0) rowStyle.append("height:${it}pt;")
        }
        val isHeaderRow = row.attrOrNull("rowType") == "headerRow"

        val cellsHtml = StringBuilder()
        forEachChildElement(row) { cellEl ->
            if (cellEl.tagName == "cell") {
                cellsHtml.append(renderCell(cellEl, isHeaderRow))
            }
        }
        return "<tr style=\"$rowStyle\">$cellsHtml</tr>"
    }

    private fun renderCell(cell: Element, isHeaderRow: Boolean): String {
        val style = StringBuilder()

        when (cell.attrOrNull("align")) {
            "vcenter" -> style.append("vertical-align:middle;")
            "bottom" -> style.append("vertical-align:bottom;")
            else -> style.append("vertical-align:top;")
        }

        colorToHex(cell.attrOrNull("fillColor"))?.let { hex ->
            if (hex != "#FFFFFF") style.append("background-color:$hex;")
        }

        if (cell.attrOrNull("border") != "borderNone") {
            val spec = cell.attrOrNull("borderSpec")?.toIntOrNull() ?: 15
            val width = cell.attrOrNull("borderWidth")?.toDoubleOrNull() ?: 1.0
            val cssBorderStyle = when (cell.attrOrNull("borderStyle")) {
                "borderStyle-dotted" -> "dotted"
                "borderStyle-dashed" -> "dashed"
                "borderStyle-double" -> "double"
                else -> "solid"
            }
            val color = colorToHex(cell.attrOrNull("borderColor")) ?: "#000000"
            val edge = "${width}pt $cssBorderStyle $color;"
            if (spec and 1 != 0) style.append("border-top:$edge")
            if (spec and 2 != 0) style.append("border-right:$edge")
            if (spec and 4 != 0) style.append("border-bottom:$edge")
            if (spec and 8 != 0) style.append("border-left:$edge")
        }

        if (isHeaderRow) style.append("font-weight:bold;")

        val colspan = cell.attrOrNull("colspan")?.toIntOrNull() ?: 1
        val colspanAttr = if (colspan > 1) " colspan=\"$colspan\"" else ""

        val inner = StringBuilder()
        forEachChildElement(cell) { child ->
            inner.append(renderStructuralElement(child))
        }

        return "<td$colspanAttr style=\"$style\">$inner</td>"
    }

    // --- text pool access ---

    /** Extracts the substring of the shared content pool referenced by an element's offset/length. */
    private fun extractText(el: Element): String {
        val start = el.attrOrNull("startOffset")?.toIntOrNull() ?: return ""
        val length = el.attrOrNull("length")?.toIntOrNull() ?: return ""
        if (start < 0 || length <= 0 || start >= contentPool.length) return ""
        val end = (start + length).coerceAtMost(contentPool.length)
        if (end <= start) return ""
        return contentPool.substring(start, end)
    }
}

// region --- small shared helpers ---

private fun Element.attrOrNull(name: String): String? {
    if (!hasAttribute(name)) return null
    val v = getAttribute(name)
    return v.ifBlank { null }
}

private inline fun forEachChildElement(parent: Element, action: (Element) -> Unit) {
    val children = parent.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            action(node as Element)
        }
    }
}

/** Converts a signed or unsigned ARGB/RGB integer string (as used throughout UDF) to a #RRGGBB hex string. */
private fun colorToHex(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        val v = value.trim().toLong()
        val rgb = (v and 0xFFFFFFL).toInt()
        String.format("#%06X", rgb)
    } catch (e: NumberFormatException) {
        null
    }
}

private fun htmlEscape(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

// endregion