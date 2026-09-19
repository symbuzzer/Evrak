package com.avalibeyaz.evrak.ui

import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
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
fun HtmlViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: (() -> Unit)? = null,
    originalExtension: String? = null,
    originalFilePath: String? = null,
    onRenameClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isConverting by remember { mutableStateOf(false) }
    var conversionMessage by remember { mutableStateOf("") }
    var isFullScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    DisposableEffect(isFullScreen) {
        val window = context.findActivity()?.window
        if (window != null) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (isFullScreen) {
                val w = context.findActivity()?.window
                if (w != null) {
                    val controller = WindowCompat.getInsetsController(w, w.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
    val defaultConvertingMessage = stringResource(id = R.string.converting)
    var showFormatDialog by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val saveHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    File(filePath).inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    conversionMessage = defaultConvertingMessage
                    isConverting = true
                }
                try {
                    val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                    val tempPdf = File(context.cacheDir, pdfName)
                    val result = DocumentConverter.convert(File(originalFilePath ?: filePath), tempPdf, context)
                    if (result is DocumentConverter.ConversionResult.Success) {
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            tempPdf.inputStream().use { input -> input.copyTo(output) }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                        }
                    } else if (result is DocumentConverter.ConversionResult.Error) {
                        withContext(Dispatchers.Main) {
                            loadError = context.getString(R.string.error_conversion_failed, result.message)
                            Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isConverting = false
                }
            }
        }
    }

    val htmlContent = remember(filePath) {
        try {
            var content = File(filePath).readText(Charsets.UTF_8)
            val isExcel = originalExtension?.contains("XLS", true) == true
            
            val tableStyle = if (isExcel) {
                "table { border-collapse: collapse; width: auto; min-width: 100%; margin-bottom: 20px; table-layout: auto; }"
            } else {
                "table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }"
            }
            
            val cellStyle = if (isExcel) {
                "th, td { border: 1px solid #ccc; padding: 8px; text-align: left; white-space: nowrap; }"
            } else {
                "th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }"
            }

            val css = """
                <style>
                    $tableStyle
                    $cellStyle
                    th { background-color: #f2f2f2; }
                    body { font-family: sans-serif; padding: 10px; }
                    img { max-width: 100%; height: auto; }
                </style>
            """.trimIndent()
            
            if (content.contains("<head>", ignoreCase = true)) {
                content = content.replace("<head>", "<head>$css", ignoreCase = true)
            } else {
                content = "<html><head>$css</head><body>$content</body></html>"
            }
            content
        } catch (e: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        MarqueeTitle(
                            title = displayName,
                            onRenameClick = onRenameClick
                        )
                    },
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
                                    Text(stringResource(id = R.string.full_screen))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { isFullScreen = true }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = stringResource(id = R.string.full_screen))
                            }
                        }
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
                            IconButton(onClick = {
                                showFormatDialog = "save"
                            }) {
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
                            IconButton(onClick = {
                                showFormatDialog = "share"
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.apply {
                            javaScriptEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            allowFileAccess = true
                            allowContentAccess = true
                            domStorageEnabled = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        
                        if (htmlContent != null) {
                            loadDataWithBaseURL("https://evrak.app/", htmlContent, "text/html", "UTF-8", null)
                        } else {
                            loadUrl("file://$filePath")
                        }
                    }
                },
                update = { },
                modifier = Modifier.fillMaxSize()
            )

            WaitScreenOverlay(
                show = isConverting,
                message = conversionMessage
            )
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

    if (showFormatDialog != null) {
        val ext = originalExtension ?: filePath.substringAfterLast(".").uppercase()
        FormatSelectionDialog(
            extension = ext,
            onDismiss = { showFormatDialog = null },
            onFormatSelected = { usePdf ->
                if (showFormatDialog == "save") {
                    if (usePdf) {
                        val newName = displayName.substringBeforeLast(".") + ".pdf"
                        savePdfLauncher.launch(newName)
                    } else {
                        if (onSaveClick != null) {
                            onSaveClick()
                        } else {
                            saveHtmlLauncher.launch(displayName)
                        }
                    }
                } else {
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            withContext(Dispatchers.Main) {
                                conversionMessage = defaultConvertingMessage
                                isConverting = true
                            }
                            try {
                                val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convert(File(originalFilePath ?: filePath), tempPdf, context)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    DocumentConverter.shareFile(context, tempPdf, "application/pdf")
                                } else if (result is DocumentConverter.ConversionResult.Error) {
                                    withContext(Dispatchers.Main) {
                                        loadError = context.getString(R.string.error_conversion_failed, result.message)
                                    }
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
