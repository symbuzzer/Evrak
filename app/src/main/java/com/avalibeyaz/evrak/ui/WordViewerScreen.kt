package com.avalibeyaz.evrak.ui

import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.xwpf.usermodel.*
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDocx by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                
                if (!file.exists()) {
                    htmlContent = wrapInHtml("<div class='error'><h3>Hata</h3><p>Dosya bulunamadı.</p></div>")
                    return@withContext
                }

                if (filePath.endsWith(".docx", ignoreCase = true)) {
                    isDocx = true
                    val bytes = file.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    htmlContent = wrapInDocxHtml(base64)
                } else if (filePath.endsWith(".doc", ignoreCase = true)) {
                    isDocx = false
                    val fis = FileInputStream(file)
                    val doc = HWPFDocument(fis)
                    val range = doc.range
                    val html = StringBuilder()
                    
                    for (i in 0 until range.numParagraphs()) {
                        val para = range.getParagraph(i)
                        html.append("<p>")
                        for (j in 0 until para.numCharacterRuns()) {
                            val run = para.getCharacterRun(j)
                            var text = run.text()
                            if (text.isNotEmpty()) {
                                if (run.isBold) text = "<b>$text</b>"
                                if (run.isItalic) text = "<i>$text</i>"
                                if (run.underlineCode != 0) text = "<u>$text</u>"
                                
                                val color = run.color
                                if (color != -1 && color != 0) {
                                    val runHexColor = String.format("#%06X", (0xFFFFFF and color))
                                    text = "<span style='color:$runHexColor;'>$text</span>"
                                }
                                html.append(text)
                            }
                        }
                        html.append("</p>")
                    }
                    doc.close()
                    fis.close()
                    htmlContent = wrapInHtml(html.toString())
                } else {
                    htmlContent = wrapInHtml("<div class='info'><p>Dosya formatı bu görünümde desteklenmiyor.</p></div>")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorTitle = e.javaClass.simpleName
                val errorMsg = e.localizedMessage ?: "Bilinmeyen hata"
                htmlContent = wrapInHtml("""
                    <div class='error'>
                        <h3>Dosya okunamadı</h3>
                        <p><strong>$errorTitle</strong></p>
                        <p>$errorMsg</p>
                    </div>
                """.trimIndent())
            } finally {
                isLoading = false
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    File(filePath).inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    IconButton(onClick = { saveLauncher.launch(displayName) }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.javaScriptEnabled = true
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
    }
}

private fun processParagraph(para: XWPFParagraph, isList: Boolean): String {
    val align = when (para.alignment) {
        ParagraphAlignment.LEFT -> "left"
        ParagraphAlignment.CENTER -> "center"
        ParagraphAlignment.RIGHT -> "right"
        ParagraphAlignment.BOTH -> "justify"
        else -> "left"
    }
    
    val style = StringBuilder()
    style.append("text-align: $align;")
    
    // Indentation
    if (para.indentationLeft != -1) {
        style.append("margin-left: ${para.indentationLeft / 14.4}pt;") 
    }
    if (para.indentationRight != -1) {
        style.append("margin-right: ${para.indentationRight / 14.4}pt;")
    }
    if (para.indentationFirstLine != -1) {
        style.append("text-indent: ${para.indentationFirstLine / 14.4}pt;")
    }
    
    // Spacing
    if (para.spacingBefore != -1) {
        style.append("margin-top: ${para.spacingBefore / 14.4}pt;")
    }
    if (para.spacingAfter != -1) {
        style.append("margin-bottom: ${para.spacingAfter / 14.4}pt;")
    }
    if (para.spacingBetween != -1.0) {
        style.append("line-height: ${para.spacingBetween / 240.0};")
    }

    val tag = if (isList) "li" else "p"
    
    val content = StringBuilder()
    para.runs.forEach { run ->
        var text = run.getText(0) ?: ""
        if (text.isNotEmpty()) {
            text = text.replace("\n", "<br/>")
            
            val runStyle = StringBuilder()
            if (run.isBold) runStyle.append("font-weight: bold;")
            if (run.isItalic) runStyle.append("font-style: italic;")
            if (run.underline != UnderlinePatterns.NONE) runStyle.append("text-decoration: underline;")
            
            val color = run.color
            if (color != null && color != "auto") {
                runStyle.append("color: #$color;")
            }
            
            @Suppress("DEPRECATION")
            val fontSize = run.fontSize
            if (fontSize != -1) {
                runStyle.append("font-size: ${fontSize}pt;")
            }
            
            // Safer highlight check
            try {
                val highlight = run.textHighlightColor
                if (highlight != null && highlight.toString() != "NONE") {
                    runStyle.append("background-color: ${highlight.toString().lowercase()};")
                }
            } catch (e: Exception) {}

            content.append("<span style='$runStyle'>$text</span>")
        }
    }
    
    return "<$tag style='$style'>$content</$tag>"
}

private fun processTable(table: XWPFTable): String {
    val html = StringBuilder()
    html.append("<table style='border-collapse:collapse; width:100%; border:1px solid #999; margin: 15px 0;'>")
    table.rows.forEach { row ->
        html.append("<tr>")
        row.tableCells.forEach { cell ->
            val cellStyle = StringBuilder()
            cellStyle.append("border: 1px solid #999; padding: 10px; vertical-align: top;")
            
            val color = cell.color
            if (color != null && color != "auto") {
                cellStyle.append("background-color: #$color;")
            }
            
            html.append("<td style='$cellStyle'>")
            
            var cellInList = false
            cell.bodyElements.forEach { element ->
                when (element) {
                    is XWPFParagraph -> {
                        val isList = element.numID != null
                        if (isList && !cellInList) {
                            html.append("<ul>")
                            cellInList = true
                        } else if (!isList && cellInList) {
                            html.append("</ul>")
                            cellInList = false
                        }
                        html.append(processParagraph(element, isList))
                    }
                    is XWPFTable -> {
                        if (cellInList) {
                            html.append("</ul>")
                            cellInList = false
                        }
                        html.append(processTable(element))
                    }
                }
            }
            if (cellInList) html.append("</ul>")
            
            html.append("</td>")
        }
        html.append("</tr>")
    }
    html.append("</table>")
    return html.toString()
}

private fun wrapInHtml(body: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=850, user-scalable=yes">
            <style>
                html {
                    height: 100%;
                }
                body {
                    min-height: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #FFFFFF;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                }
                .document-container {
                    background-color: #ffffff;
                    width: 850px;
                    padding: 60px 80px;
                    margin: auto 0;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                    box-sizing: border-box;
                    color: #2c3e50;
                    line-height: 1.6;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    border-radius: 4px;
                    flex-shrink: 0;
                }
                h1, h2, h3 { color: #1a2a3a; font-family: 'Segoe UI', Arial, sans-serif; margin-bottom: 20px; }
                p { margin-top: 0; margin-bottom: 12px; }
                table { border-collapse: collapse; margin: 25px 0; border: 1px solid #dcdde1; width: 100% !important; table-layout: fixed; }
                th, td { border: 1px solid #dcdde1; padding: 12px; text-align: left; }
                img { max-width: 100%; height: auto; border-radius: 2px; }
                ul { padding-left: 30px; margin-bottom: 20px; }
                li { margin-bottom: 8px; }
                .error { text-align: center; padding: 60px; color: #e74c3c; }
                .info { text-align: center; padding: 60px; color: #7f8c8d; }
            </style>
        </head>
        <body>
            <div class="document-container">
                $body
            </div>
        </body>
        </html>
    """.trimIndent()
}

private fun wrapInDocxHtml(base64Data: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=850, user-scalable=yes">
            <script src="https://unpkg.com/jszip/dist/jszip.min.js"></script>
            <script src="https://unpkg.com/docx-preview/dist/docx-preview.js"></script>
            <style>
                html {
                    height: 100%;
                }
                body {
                    min-height: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #FFFFFF;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                }
                #container { 
                    background-color: #ffffff;
                    width: 850px;
                    margin: auto 0;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                    box-sizing: border-box;
                    border-radius: 4px;
                    flex-shrink: 0;
                }
                .docx-wrapper { padding: 40px 60px !important; background-color: transparent !important; }
                .docx { box-shadow: none !important; margin-bottom: 0 !important; border: none !important; }
            </style>
        </head>
        <body>
            <div id="container"></div>
            <script>
                function renderDocx(base64) {
                    const binaryString = window.atob(base64);
                    const bytes = new Uint8Array(binaryString.length);
                    for (let i = 0; i < binaryString.length; i++) {
                        bytes[i] = binaryString.charCodeAt(i);
                    }
                    const blob = new Blob([bytes.buffer], { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" });
                    const container = document.getElementById("container");
                    docx.renderAsync(blob, container, null, {
                        className: "docx",
                        inWrapper: true,
                        ignoreWidth: false,
                        ignoreHeight: false,
                        debug: false
                    }).then(() => {
                        console.log("docx: finished");
                    });
                }
                renderDocx("$base64Data");
            </script>
        </body>
        </html>
    """.trimIndent()
}
