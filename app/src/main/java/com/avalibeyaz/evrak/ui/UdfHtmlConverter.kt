package com.avalibeyaz.evrak.ui

import android.content.Context
import com.avalibeyaz.evrak.R
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object UdfHtmlConverter {

    data class UdfBody(
        val html: String,
        val pageWidthPt: Double,
        val pageHeightPt: Double,
        val topMargin: Double,
        val bottomMargin: Double,
        val leftMargin: Double,
        val rightMargin: Double,
        val backgroundImageBase64: String? = null
    )

    data class UdfFileData(val xml: String, val backgroundImageBase64: String? = null)

    fun readUdfFileData(file: File): UdfFileData? {
        return try {
            ZipFile(file).use { zip ->
                val contentEntry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/').equals("content.xml", ignoreCase = true)
                } ?: return null
                
                val xml = zip.getInputStream(contentEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                
                val bgEntry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && 
                    (it.name.endsWith(".jpg", ignoreCase = true) || 
                     it.name.endsWith(".png", ignoreCase = true) ||
                     it.name.contains("arkaplan", ignoreCase = true) ||
                     it.name.contains("zemin", ignoreCase = true) ||
                     it.name.contains("watermark", ignoreCase = true) ||
                     it.name.contains("filigran", ignoreCase = true))
                }
                
                val bgBase64 = bgEntry?.let { entry ->
                    zip.getInputStream(entry).use { stream ->
                        android.util.Base64.encodeToString(stream.readBytes(), android.util.Base64.NO_WRAP)
                    }
                }
                
                UdfFileData(xml, bgBase64)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun convertUdfToHtml(file: File, context: Context): String {
        val udfData = readUdfFileData(file) ?: return ""
        val body = UdfDocumentParser(udfData.xml, context, udfData.backgroundImageBase64).parseToHtmlBody()
        return wrapUdfHtml(
            body.html,
            body.pageWidthPt,
            body.pageHeightPt,
            body.topMargin,
            body.bottomMargin,
            body.leftMargin,
            body.rightMargin,
            body.backgroundImageBase64
        )
    }

    fun wrapUdfHtml(
        body: String,
        pageWidthPt: Double = 595.28,
        pageHeightPt: Double = 841.89,
        topMargin: Double = 56.7,
        bottomMargin: Double = 56.7,
        leftMargin: Double = 56.7,
        rightMargin: Double = 56.7,
        backgroundImageBase64: String? = null
    ): String {
        val bgStyle = if (backgroundImageBase64 != null) {
            "background-image: url('data:image/*;base64,$backgroundImageBase64'); background-repeat: repeat; background-size: auto;"
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="tr">
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
                        font-family: 'Times New Roman', 'Liberation Serif', serif;
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
                        $bgStyle
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
                    .udf-paragraph { 
                        margin: 0; 
                        padding: 0; 
                        white-space: pre-wrap;
                    }
                    .udf-page-break { border-top: 1px dashed #bbbbbb; margin: 24pt 0; }
                    table.udf-table { border-collapse: collapse; margin: 8pt 0; width: 100%; table-layout: fixed; }
                    table.udf-table td { padding: 5.4pt; vertical-align: top; word-break: break-word; }
                    .udf-tab { display: inline-block; min-width: 28pt; white-space: pre; }
                    .udf-list-row { display: grid; width: 100%; align-items: flex-start; }
                    .udf-list-marker { grid-column: 1; padding-right: 8pt; white-space: nowrap; }
                    .udf-list-content { grid-column: 2; min-width: 0; overflow-wrap: anywhere; }
                    .udf-verification-bar { 
                        border-top: 1px solid #333; 
                        margin-top: 24pt; 
                        padding-top: 6pt; 
                        font-size: 8pt; 
                        color: #333; 
                        text-align: center;
                    }
                    img.udf-image { max-width: 100%; height: auto; }
                    .error { text-align: center; padding: 60px; color: #e74c3c; }
                    .info { text-align: center; padding: 60px; color: #7f8c8d; }
                    
                    @media print {
                        html, body {
                            height: auto !important;
                            min-height: 0 !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            background-color: #ffffff !important;
                        }
                        .udf-page {
                            margin: 0 !important;
                            box-shadow: none !important;
                            width: 100% !important;
                            min-height: 0 !important;
                        }
                    }
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

    private data class UdfStyle(
        val family: String? = null,
        val size: String? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val foreground: String? = null
    )

    private class UdfDocumentParser(
        private val xml: String,
        private val context: Context,
        private var backgroundImageBase64: String? = null
    ) {
        private var contentPool: String = ""
        private var resolverStyleName: String = "default"
        private val styles = mutableMapOf<String, UdfStyle>()
        private val numberedListCounters = mutableMapOf<String, MutableMap<Int, Int>>()
        private val dataMap = mutableMapOf<String, String>()
        private var webId: String? = null
        private var defaultHanging: Double = 142.0

        fun parseToHtmlBody(): UdfBody {
            val factory = DocumentBuilderFactory.newInstance().apply {
                try {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                } catch (_: Exception) {}
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream(Charsets.UTF_8))
            val root = doc.documentElement ?: return UdfBody("<div class='error'><p>${context.getString(R.string.udf_invalid_content)}</p></div>", 595.28, 841.89, 56.7, 56.7, 56.7, 56.7)

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
            var dataEl: Element? = null

            forEachChildElement(root) { child ->
                when (child.tagName) {
                    "content" -> contentEl = child
                    "properties" -> propertiesEl = child
                    "elements" -> elementsEl = child
                    "styles" -> stylesEl = child
                    "webID" -> webId = child.attrOrNull("id")
                    "data" -> dataEl = child
                }
            }

            contentPool = contentEl?.textContent ?: ""
            stylesEl?.let { parseStyles(it) }
            dataEl?.let { parseData(it) }

            val hangingValues = mutableListOf<Double>()
            elementsEl?.let { elements ->
                forEachChildElement(elements) { child ->
                    if (child.tagName == "paragraph") {
                        child.attrOrNull("Hanging")?.toDoubleOrNull()?.let { 
                            if (it > 0) hangingValues.add(it) 
                        }
                    }
                }
            }
            defaultHanging = hangingValues.groupBy { it }.maxByOrNull { it.value.size }?.key ?: 142.0

            propertiesEl?.let { props ->
                if (backgroundImageBase64 == null) {
                    backgroundImageBase64 = props.attrOrNull("backgroundImageData") ?: props.attrOrNull("arkaplanResmi")
                }

                forEachChildElement(props) { child ->
                    if (child.tagName == "pageFormat") {
                        val orientation = child.attrOrNull("paperOrientation")
                        val w = 595.28
                        val h = 841.89

                        if (orientation == "0" || orientation == "2") {
                            pageWidthPt = h
                            pageHeightPt = w
                        } else {
                            pageWidthPt = w
                            pageHeightPt = h
                        }

                        child.attrOrNull("topMargin")?.toDoubleOrNull()?.let { topMargin = it }
                        child.attrOrNull("bottomMargin")?.toDoubleOrNull()?.let { bottomMargin = it }
                        child.attrOrNull("leftMargin")?.toDoubleOrNull()?.let { leftMargin = it }
                        child.attrOrNull("rightMargin")?.toDoubleOrNull()?.let { rightMargin = it }
                        
                        if (backgroundImageBase64 == null) {
                            backgroundImageBase64 = child.attrOrNull("backgroundImageData") ?: child.attrOrNull("arkaplanResmi")
                        }
                    } else if (child.tagName == "backgroundImageData" || child.tagName == "arkaplanResmi") {
                        if (backgroundImageBase64 == null) {
                            backgroundImageBase64 = child.textContent.trim()
                        }
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
                elementsSb.append("<div class='info'><p>${context.getString(R.string.udf_no_content)}</p></div>")
            }

            webId?.let {
                elementsSb.append("<div class='udf-verification-bar'>")
                elementsSb.append(context.getString(R.string.udf_verification_bar, it))
                elementsSb.append("</div>")
            }

            return UdfBody(elementsSb.toString(), pageWidthPt, pageHeightPt, topMargin, bottomMargin, leftMargin, rightMargin, backgroundImageBase64)
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

        private fun parseData(dataEl: Element) {
            forEachChildElement(dataEl) { child ->
                val key = child.tagName
                val value = child.textContent?.trim() ?: ""
                if (value.isNotEmpty()) {
                    dataMap[key] = value
                }
            }
        }

        private fun defaultStyle(): UdfStyle =
            styles[resolverStyleName] ?: styles["default"] ?: UdfStyle(
                family = "Times New Roman",
                size = "11",
                foreground = "#000000"
            )

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

        private fun renderParagraph(p: Element): String {
            val style = StringBuilder()
            when (p.attrOrNull("Alignment")) {
                "1" -> style.append("text-align:center;")
                "2" -> style.append("text-align:right;")
                "3" -> style.append("text-align:justify; text-justify:inter-word; text-align-last:left;")
                else -> style.append("text-align:left;")
            }

            val leftIndentAttr = p.attrOrNull("LeftIndent")?.toDoubleOrNull()
            p.attrOrNull("RightIndent")?.toDoubleOrNull()?.let { style.append("margin-right:${it}pt;") }
            p.attrOrNull("FirstLineIndent")?.toDoubleOrNull()?.let { style.append("text-indent:${it}pt;") }
            p.attrOrNull("SpaceAbove")?.toDoubleOrNull()?.let { style.append("margin-top:${it}pt;") }
            p.attrOrNull("SpaceBelow")?.toDoubleOrNull()?.let { style.append("margin-bottom:${it}pt;") }
            p.attrOrNull("LineSpacing")?.toDoubleOrNull()?.let { style.append("line-height:${1.0 + it};") }

            val listLevel = p.attrOrNull("ListLevel")?.toIntOrNull() ?: 1
            val secListType = p.attrOrNull("SecListTypeLevel$listLevel")
            val isNumbered = p.attrOrNull("Numbered")?.toBoolean() == true
            val isBulleted = p.attrOrNull("Bulleted")?.toBoolean() == true || secListType?.startsWith("BULLET_TYPE_") == true
            val hangingAttr = p.attrOrNull("Hanging")?.toDoubleOrNull() ?: 0.0

            var hasTab = false
            forEachChildElement(p) { child ->
                if (child.tagName == "tab") hasTab = true
                if (child.tagName == "content") {
                    if (extractText(child).contains('\t')) hasTab = true
                }
            }

            if (isNumbered || isBulleted || hangingAttr > 0 || (hasTab && p.attrOrNull("Alignment") != "1")) {
                val marker: String
                val markerWidth: Double
                val body: String

                if (isNumbered || isBulleted) {
                    val listId = p.attrOrNull("ListId") ?: "default"
                    val levelCounters = numberedListCounters.getOrPut(listId) { mutableMapOf() }

                    marker = if (isBulleted) {
                        htmlEscape(bulletMarker(secListType ?: p.attrOrNull("BulletType")))
                    } else {
                        val n = (levelCounters[listLevel] ?: 0) + 1
                        levelCounters[listLevel] = n
                        
                        // Reset sub-level counters when parent level increments
                        val levelsToRemove = levelCounters.keys.filter { it > listLevel }
                        levelsToRemove.forEach { levelCounters.remove(it) }

                        htmlEscape(numberMarker(n, p.attrOrNull("NumberType")))
                    }

                    markerWidth = 20.0
                    leftIndentAttr?.let { style.append("padding-left:${it}pt;") }

                    val inner = StringBuilder()
                    forEachChildElement(p) { child -> inner.append(renderInlineElement(child)) }
                    body = inner.toString()
                } else {
                    val split = renderParagraphWithHangingSplit(p, hangingAttr)
                    if (split != null) {
                        marker = split.first
                        val plainMarker = marker.replace(Regex("<[^>]*>"), "").replace("&nbsp;", "").trim()
                        val isIndentOnly = plainMarker.isEmpty()
                        
                        markerWidth = if (isIndentOnly) 28.0 
                                      else if (hangingAttr > 0) hangingAttr 
                                      else defaultHanging
                        body = split.second
                    } else {
                        val inner = StringBuilder()
                        forEachChildElement(p) { child -> inner.append(renderInlineElement(child)) }
                        if (hangingAttr > 0) {
                            return "<div class=\"udf-paragraph\" style=\"$style padding-left:${hangingAttr}pt; text-indent:-${hangingAttr}pt;\">$inner</div>"
                        }
                        return "<div class=\"udf-paragraph\" style=\"$style\">$inner</div>"
                    }
                }

                val gridCols = "minmax(${markerWidth}pt, max-content) 1fr"
                return "<div class=\"udf-paragraph\" style=\"$style\"><div class=\"udf-list-row\" style=\"grid-template-columns: $gridCols;\"><div class=\"udf-list-marker\">$marker&nbsp;</div><div class=\"udf-list-content\">$body</div></div></div>"
            }

            val innerSb = StringBuilder()
            forEachChildElement(p) { child ->
                innerSb.append(renderInlineElement(child))
            }
            
            var inner = innerSb.toString()
            if (inner.isEmpty()) inner = "&nbsp;"

            return "<div class=\"udf-paragraph\" style=\"$style\">$inner</div>"
        }

        private fun renderParagraphWithHangingSplit(p: Element, hanging: Double): Pair<String, String>? {
            val before = StringBuilder()
            val after = StringBuilder()
            var splitDone = false

            forEachChildElement(p) { child ->
                if (!splitDone) {
                    if (child.tagName == "tab") {
                        splitDone = true
                        return@forEachChildElement
                    }
                    if (child.tagName == "content") {
                        val text = extractText(child)
                        val tabIndex = text.indexOf('\t')
                        if (tabIndex >= 0) {
                            val styleStr = contentRunStyle(child)
                            val preText = text.substring(0, tabIndex).trimEnd('\t', ' ')
                            
                            var postStart = tabIndex
                            while (postStart < text.length && (text[postStart] == '\t' || text[postStart] == ' ')) postStart++
                            val postText = text.substring(postStart)

                            if (preText.isNotEmpty()) {
                                before.append("<span style=\"$styleStr\">${htmlEscape(preText)}</span>")
                            }
                            if (postText.isNotEmpty()) {
                                val trimmedPostText = postText.trim('\n', '\r')
                                after.append("<span style=\"$styleStr\">${htmlEscape(trimmedPostText)}</span>")
                            }
                            splitDone = true
                            return@forEachChildElement
                        }
                    }
                }
                if (splitDone) {
                    after.append(renderInlineElement(child))
                } else {
                    before.append(renderInlineElement(child))
                }
            }

            return if (splitDone) Pair(before.toString(), after.toString()) else null
        }

        private fun numberMarker(n: Int, type: String?): String = when (type) {
            "NUMBER_TYPE_NUMBER_PARENTHESIS" -> "$n)"
            "NUMBER_TYPE_CHAR_SMALL_DOT" -> "${toAlpha(n, false)}."
            "NUMBER_TYPE_CHAR_SMALL_PARENTHESIS" -> "${toAlpha(n, false)})"
            "NUMBER_TYPE_CHAR_BIG_DOT" -> "${toAlpha(n, true)}."
            "NUMBER_TYPE_CHAR_BIG_PARENTHESIS" -> "${toAlpha(n, true)})"
            "NUMBER_TYPE_ROMAN_SMALL_DOT" -> "${toAlpha(n, false).lowercase()}."
            "NUMBER_TYPE_ROMAN_SMALL_PARENTHESIS" -> "${toAlpha(n, false).lowercase()})"
            "NUMBER_TYPE_ROMAN_BIG_DOT" -> "${toAlpha(n, true).uppercase()}."
            "NUMBER_TYPE_ROMAN_BIG_PARENTHESIS" -> "${toAlpha(n, true).uppercase()})"
            else -> "$n." 
        }

        private fun bulletMarker(type: String?): String = when (type) {
            "BULLET_TYPE_RECTANGLE" -> "\u25A0"
            "BULLET_TYPE_ARROW" -> "\u27A4"
            "BULLET_TYPE_DIAMOND" -> "\u25C6"
            "BULLET_TYPE_DIAMOND_2" -> "\u25CA"
            "BULLET_TYPE_TRIANGLE" -> "\u25B2"
            "BULLET_TYPE_RECTANGLE_D" -> "\u25A1"
            else -> "\u2022" 
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

        private fun renderInlineElement(el: Element): String {
            return try {
                when (el.tagName) {
                    "content" -> renderContentRun(el)
                    "image" -> renderImage(el)
                    "tab" -> "<span class=\"udf-tab\">&nbsp;</span>"
                    "space" -> htmlEscape(extractText(el)).ifEmpty { "&nbsp;" }
                    "field" -> renderFieldRun(el)
                    else -> ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        private fun contentRunStyle(el: Element): String {
            val runStyle = StringBuilder()
            val family = el.attrOrNull("family") ?: defaultStyle().family
            val size = el.attrOrNull("size") ?: defaultStyle().size
            
            family?.let { 
                val fontStack = when {
                    it.equals("Arial", true) -> "Arial, 'Liberation Sans', Helvetica, sans-serif"
                    it.equals("Times New Roman", true) -> "'Times New Roman', 'Liberation Serif', serif"
                    else -> "'$it', 'Liberation Serif', serif"
                }
                runStyle.append("font-family:$fontStack;") 
            }
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
            return runStyle.toString()
        }

        private fun renderTextWithTabs(text: String, styleStr: String): String {
            if (text.isEmpty()) return ""
            val cleanText = text.trim('\n', '\r')
            if (cleanText.isEmpty()) return ""

            val out = StringBuilder()
            val chunk = StringBuilder()
            var i = 0

            fun flushChunk() {
                if (chunk.isNotEmpty()) {
                    out.append("<span style=\"$styleStr\">${htmlEscape(chunk.toString())}</span>")
                    chunk.clear()
                }
            }

            while (i < cleanText.length) {
                if (cleanText[i] == '\t') {
                    flushChunk()
                    out.append("<span class=\"udf-tab\">&nbsp;</span>")
                    i++
                } else {
                    chunk.append(cleanText[i])
                    i++
                }
            }
            flushChunk()
            return out.toString()
        }

        private fun renderContentRun(el: Element): String {
            val text = extractText(el)
            if (text.isEmpty() || text == "\u200B") return ""
            return renderTextWithTabs(text, contentRunStyle(el))
        }

        private fun renderFieldRun(el: Element): String {
            val fieldName = el.attrOrNull("fieldName") ?: el.attrOrNull("name") ?: ""
            val mappedValue = dataMap[fieldName]
            if (!mappedValue.isNullOrEmpty() && mappedValue != fieldName) {
                return renderTextWithTabs(mappedValue, contentRunStyle(el))
            }
            val valueAttr = el.attrOrNull("value")
            if (!valueAttr.isNullOrEmpty() && valueAttr != fieldName) {
                return renderTextWithTabs(valueAttr, contentRunStyle(el))
            }
            val defaultAttr = el.attrOrNull("default")
            if (!defaultAttr.isNullOrEmpty() && defaultAttr != fieldName) {
                return renderTextWithTabs(defaultAttr, contentRunStyle(el))
            }
            val poolText = extractText(el)
            if (poolText.isNotEmpty() && poolText != "\u200B" && poolText != fieldName) {
                return renderTextWithTabs(poolText, contentRunStyle(el))
            }
            val fallback = valueAttr ?: defaultAttr ?: mappedValue ?: poolText.takeIf { it.isNotEmpty() } ?: "[$fieldName]"
            return renderTextWithTabs(fallback, contentRunStyle(el))
        }

        private fun renderImage(el: Element): String {
            val data = el.attrOrNull("imageData") ?: return ""
            val width = el.attrOrNull("width")?.toDoubleOrNull()
            val height = el.attrOrNull("height")?.toDoubleOrNull()
            val style = StringBuilder()
            width?.let { style.append("width:${it}pt;") }
            height?.let { style.append("height:${it}pt;") }
            return "<img class=\"udf-image\" style=\"$style\" src=\"data:image/*;base64,$data\" />"
        }

        private fun renderTable(table: Element): String {
            val hasBorder = table.attrOrNull("border") != "borderNone"
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
                    rowsHtml.append(renderRow(rowEl, hasBorder))
                }
            }

            return "<table class=\"udf-table\">$colGroup<tbody>$rowsHtml</tbody></table>"
        }

        private fun renderRow(row: Element, tableHasBorder: Boolean): String {
            val rowStyle = StringBuilder()
            row.attrOrNull("height")?.toDoubleOrNull()?.let {
                if (it > 0) rowStyle.append("height:${it}pt;")
            }
            val isHeaderRow = row.attrOrNull("rowType") == "headerRow"

            val cellsHtml = StringBuilder()
            forEachChildElement(row) { cellEl ->
                if (cellEl.tagName == "cell") {
                    cellsHtml.append(renderCell(cellEl, isHeaderRow, tableHasBorder))
                }
            }
            return "<tr style=\"$rowStyle\">$cellsHtml</tr>"
        }

        private fun renderCell(cell: Element, isHeaderRow: Boolean, tableHasBorder: Boolean): String {
            val style = StringBuilder()
            when (cell.attrOrNull("align")) {
                "vcenter" -> style.append("vertical-align:middle;")
                "bottom" -> style.append("vertical-align:bottom;")
                else -> style.append("vertical-align:top;")
            }

            colorToHex(cell.attrOrNull("fillColor"))?.let { hex ->
                if (hex != "#FFFFFF") style.append("background-color:$hex;")
            }

            val cellBorderAttr = cell.attrOrNull("border")
            val effectiveHasBorder = if (cellBorderAttr != null) {
                cellBorderAttr != "borderNone"
            } else {
                tableHasBorder
            }

            if (effectiveHasBorder) {
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

        private fun extractText(el: Element): String {
            val start = el.attrOrNull("startOffset")?.toIntOrNull() ?: return ""
            val length = el.attrOrNull("length")?.toIntOrNull() ?: return ""
            if (start < 0 || length <= 0 || start >= contentPool.length) return ""
            val end = (start + length).coerceAtMost(contentPool.length)
            if (end <= start) return ""
            return contentPool.substring(start, end).replace("\r\n", "\n").replace("\r", "\n")
        }
    }

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
}
