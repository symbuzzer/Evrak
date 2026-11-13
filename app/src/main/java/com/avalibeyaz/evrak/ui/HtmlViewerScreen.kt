package com.avalibeyaz.evrak.ui

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
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
    onRenameClick: (String) -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isConverting by remember { mutableStateOf(false) }
    var conversionMessage by remember { mutableStateOf("") }
    var isFullScreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var totalMatches by remember { mutableIntStateOf(0) }
    var hasSearched by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<ObservableWebView?>(null) }

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
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            val w = context.findActivity()?.window
            if (w != null) {
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val defaultConvertingMessage = stringResource(id = R.string.converting)
    var showFormatDialog by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }

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
                            loadError = FileErrorUtils.getStandardizedErrorMessage(context, result.message)
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
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            readError = FileErrorUtils.getStandardizedErrorMessage(context, "not_found")
            null
        } else {
            try {
                var content = file.readText(Charsets.UTF_8)
                val isExcel = originalExtension?.contains("XLS", true) == true
                val isPresentation = originalExtension?.contains("PPT", true) == true

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

                val presentationStyle = if (isPresentation) {
                    """
                    body { background-color: #e9ecef; padding: 16px; }
                    img { max-width: 100%; height: auto; display: block; margin: 0 auto 30px auto; box-shadow: 0 6px 20px rgba(0,0,0,0.15); border-radius: 8px; background: #fff; }
                    div, section { background: #ffffff; border-radius: 10px; box-shadow: 0 3px 10px rgba(0,0,0,0.08); margin-bottom: 30px; padding: 30px; }
                    """.trimIndent()
                } else ""

                val css = """
                    <style>
                        $tableStyle
                        $cellStyle
                        $presentationStyle
                        th { background-color: #f2f2f2; }
                        body { font-family: sans-serif; padding: 10px; }
                        img { max-width: 100%; height: auto; }
                    </style>
                """.trimIndent()

                val csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:\">"

                if (content.contains("<head>", ignoreCase = true)) {
                    content = content.replaceFirst("<head>", "<head>$csp$css", ignoreCase = true)
                } else {
                    content = "<html><head>$csp$css</head><body>$content</body></html>"
                }
                content
            } catch (e: Exception) {
                readError = FileErrorUtils.getStandardizedErrorMessage(context, e)
                null
            }
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
                                    Text(stringResource(id = R.string.search_in_document))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = {
                                isSearchOpen = !isSearchOpen
                                if (!isSearchOpen) {
                                    searchQuery = ""
                                    hasSearched = false
                                    currentMatch = 0
                                    totalMatches = 0
                                    webViewInstance?.clearMatches()
                                }
                            }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(id = R.string.search_in_document),
                                    tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
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
                                    Text(stringResource(id = R.string.more_options))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.more_options))
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.open_with)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            val targetPath = originalFilePath ?: filePath
                                            val handled = openFileWithSystemChooser(context, targetPath)
                                            if (!handled) {
                                                Toast.makeText(context, context.getString(R.string.no_other_apps), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.share)) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showFormatDialog = "share"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.save)) },
                                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showFormatDialog = "save"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.print)) },
                                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            val targetPath = originalFilePath ?: filePath
                                            printFile(context, targetPath, displayName) { converting ->
                                                isPrinting = converting
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.delete), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(text = stringResource(id = R.string.delete)) },
                text = { Text(text = stringResource(id = R.string.confirm_delete)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            if (onDeleteClick != null) {
                                onDeleteClick()
                            } else {
                                try { File(originalFilePath ?: filePath).delete() } catch (_: Exception) {}
                                onBackClick()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(text = stringResource(id = R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                }
            )
        }
        val webViewScrollState = rememberWebViewScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearchOpen && !isFullScreen) {
                DocumentSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            hasSearched = true
                            webViewInstance?.findAllAsync(searchQuery)
                        }
                    },
                    onNext = {
                        webViewInstance?.findNext(true)
                    },
                    onPrevious = {
                        webViewInstance?.findNext(false)
                    },
                    onClose = {
                        isSearchOpen = false
                        searchQuery = ""
                        hasSearched = false
                        currentMatch = 0
                        totalMatches = 0
                        webViewInstance?.clearMatches()
                    },
                    currentMatch = currentMatch,
                    totalMatches = totalMatches,
                    hasSearched = hasSearched
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (readError != null) {
                    ErrorView(
                        errorMessage = readError!!,
                        onBackClick = onBackClick
                    )
                } else if (htmlContent != null) {
                    AndroidView(
                        factory = { ctx ->
                            ObservableWebView(ctx).apply {
                                webViewInstance = this
                                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                    if (isDoneCounting) {
                                        currentMatch = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
                                        totalMatches = numberOfMatches
                                        hasSearched = true
                                    }
                                }
                                onScrollStateChanged = { offset, range, extent ->
                                    webViewScrollState.offset = offset
                                    webViewScrollState.range = range
                                    webViewScrollState.extent = extent
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        (view as? ObservableWebView)?.notifyScroll()
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val uri = request?.url
                                        if (uri != null && request.hasGesture()) {
                                            val scheme = uri.scheme?.lowercase()
                                            if (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel") {
                                                try {
                                                    ctx.startActivity(
                                                        Intent(Intent.ACTION_VIEW, uri)
                                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        return true
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        if (request == null || request.isForMainFrame) return null
                                        val scheme = request.url?.scheme?.lowercase()
                                        if (scheme == "data" || scheme == "about" || scheme == "blob") return null
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            403,
                                            "Blocked",
                                            emptyMap(),
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }
                                settings.apply {
                                    javaScriptEnabled = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    domStorageEnabled = false
                                    blockNetworkLoads = true
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                }
                                loadDataWithBaseURL("https://evrak.app/", htmlContent, "text/html", "UTF-8", null)
                            }
                        },
                        update = { },
                        modifier = Modifier.fillMaxSize()
                    )

                    DraggableScrollbar(
                        adapter = webViewScrollbarAdapter(webViewScrollState, webViewInstance)
                    )
                }

                WaitScreenOverlay(
                    show = isConverting,
                    message = conversionMessage
                )

                WaitScreenOverlay(
                    show = isPrinting,
                    message = stringResource(id = R.string.preparing)
                )
            }
        }

        loadError?.let { error ->
            ErrorDialog(
                errorMessage = error,
                onDismiss = { loadError = null }
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
                                        loadError = FileErrorUtils.getStandardizedErrorMessage(context, result.message)
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