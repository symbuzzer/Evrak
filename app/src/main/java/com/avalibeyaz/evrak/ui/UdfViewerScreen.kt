package com.avalibeyaz.evrak.ui

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
                    wrapUdfHtmlError(context, context.getString(R.string.error_file_not_found))
                } else {
                    val html = UdfHtmlConverter.convertUdfToHtml(file, context)
                    if (html.isEmpty()) {
                        wrapUdfHtmlError(context, context.getString(R.string.error_udf_read))
                    } else {
                        html
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                wrapUdfHtmlError(context, e.localizedMessage ?: context.getString(R.string.error_unknown))
            } finally {
                isLoading = false
            }
        }
    }

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
                    val result = DocumentConverter.convert(File(filePath), tempPdf, context)
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
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.save))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { showFormatDialog = "save" }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.share))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { showFormatDialog = "share" }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                        }
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

            ConversionOverlay(isConverting = isConverting)

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
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            isConverting = true
                            try {
                                val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convert(File(filePath), tempPdf, context)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    DocumentConverter.shareFile(context, tempPdf, "application/pdf")
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

private fun wrapUdfHtmlError(context: android.content.Context, message: String): String {
    val errorTitle = context.getString(R.string.error)
    return UdfHtmlConverter.wrapUdfHtml(
        """
        <div class='error'>
            <h3>$errorTitle</h3>
            <p>$message</p>
        </div>
        """.trimIndent()
    )
}
