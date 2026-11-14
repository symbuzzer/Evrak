package com.avalibeyaz.evrak.ui

import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.avalibeyaz.evrak.R
import java.io.File
import java.io.FileInputStream

class SafeFileHandler(private val baseDir: File) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): android.webkit.WebResourceResponse? {
        try {
            val file = File(baseDir, path).canonicalFile
            if (!file.absolutePath.startsWith(baseDir.canonicalPath)) return null
            if (!file.exists() || !file.isFile) return null

            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "pdf" -> "application/pdf"
                "js" -> "application/javascript"
                "mjs" -> "application/javascript"
                "html" -> "text/html"
                "css" -> "text/css"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }

            return android.webkit.WebResourceResponse(mimeType, null, FileInputStream(file))
        } catch (e: Exception) {
            return null
        }
    }
}

class PdfViewerBridge(
    private val onPageChanged: (Int, Int) -> Unit,
    private val onSearchResult: (Int, Int) -> Unit = { _, _ -> }
) {
    @android.webkit.JavascriptInterface
    fun notifyPageChanged(current: Int, total: Int) {
        onPageChanged(current, total)
    }

    @android.webkit.JavascriptInterface
    fun notifySearchResult(current: Int, total: Int) {
        onSearchResult(current, total)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: (() -> Unit)? = null,
    saveFilePath: String = filePath,
    saveDisplayName: String = displayName,
    saveMimeType: String = "application/pdf",
    onRenameClick: (String) -> Unit = {},
    enableSearch: Boolean = true,
    onDeleteClick: (() -> Unit)? = null,
    originalFilePath: String? = null
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var totalMatches by remember { mutableIntStateOf(0) }
    var hasSearched by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<ObservableWebView?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

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

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(saveMimeType)
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    File(saveFilePath).inputStream().use { input ->
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
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(id = R.string.back))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (enableSearch) {
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
                                        webViewInstance?.evaluateJavascript("clearPdfSearch()", null)
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(id = R.string.search_in_document),
                                        tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
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
                                            onShareClick()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.save)) },
                                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            if (onSaveClick != null) {
                                                onSaveClick()
                                            } else {
                                                saveLauncher.launch(saveDisplayName)
                                            }
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
        val file = File(filePath)

        val assetLoader = remember(filePath) {
            val assetsHandler = object : WebViewAssetLoader.PathHandler {
                private val inner = WebViewAssetLoader.AssetsPathHandler(context)
                override fun handle(path: String): WebResourceResponse? {
                    val response = inner.handle(path)
                    if (path.endsWith(".mjs", ignoreCase = true) || path.endsWith(".js", ignoreCase = true)) {
                        response?.mimeType = "application/javascript"
                    }
                    return response
                }
            }

            WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", assetsHandler)
                .addPathHandler("/internal/", SafeFileHandler(context.filesDir))
                .addPathHandler("/cache/", SafeFileHandler(context.cacheDir))
                .build()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (enableSearch && isSearchOpen && !isFullScreen) {
                DocumentSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            hasSearched = true
                            val escaped = searchQuery.replace("\\", "\\\\").replace("'", "\\'")
                            webViewInstance?.evaluateJavascript("searchPdf('$escaped')", null)
                        }
                    },
                    onNext = {
                        webViewInstance?.evaluateJavascript("nextPdfMatch()", null)
                    },
                    onPrevious = {
                        webViewInstance?.evaluateJavascript("prevPdfMatch()", null)
                    },
                    onClose = {
                        isSearchOpen = false
                        searchQuery = ""
                        hasSearched = false
                        currentMatch = 0
                        totalMatches = 0
                        webViewInstance?.evaluateJavascript("clearPdfSearch()", null)
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
                if (loadError != null) {
                    ErrorView(
                        errorMessage = loadError!!,
                        onBackClick = onBackClick
                    )
                } else {
                    val webViewScrollState = rememberWebViewScrollState()
                    var currentPage by remember { mutableIntStateOf(1) }
                    var totalPages by remember { mutableIntStateOf(0) }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                ObservableWebView(ctx).apply {
                                    webViewInstance = this
                                    addJavascriptInterface(PdfViewerBridge(
                                        onPageChanged = { current, total ->
                                            post {
                                        currentPage = current
                                        totalPages = total
                                        notifyScroll()
                                    }
                                        },
                                        onSearchResult = { current, total ->
                                            post {
                                                currentMatch = current
                                                totalMatches = total
                                                hasSearched = true
                                            }
                                        }
                                    ), "AndroidBridge")
                                    onScrollStateChanged = { offset, range, extent ->
                                        webViewScrollState.offset = offset
                                        webViewScrollState.range = range
                                        webViewScrollState.extent = extent
                                    }
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    @Suppress("DEPRECATION")
                                    settings.textZoom = 100
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.setSupportZoom(false)
                                    settings.builtInZoomControls = false
                                    settings.displayZoomControls = false

                                    isVerticalScrollBarEnabled = true
                                    isHorizontalScrollBarEnabled = true

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                            val msg = consoleMessage?.message() ?: ""
                                            Log.d("PdfViewerJS", "$msg -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                            if (msg.contains("InvalidPDFException", true) ||
                                                msg.contains("Invalid or corrupted PDF", true) ||
                                                msg.contains("MissingPDFException", true) ||
                                                msg.contains("UnexpectedResponseException", true) ||
                                                msg.contains("An error occurred while loading the PDF", true)) {
                                                loadError = FileErrorUtils.getStandardizedErrorMessage(context, msg)
                                                isLoading = false
                                            }
                                            return true
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest
                                        ): WebResourceResponse? {
                                            return assetLoader.shouldInterceptRequest(request.url)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            (view as? ObservableWebView)?.let { owv ->
                                                owv.notifyScroll()
                                                owv.postDelayed({ owv.notifyScroll() }, 500)
                                                owv.postDelayed({ owv.notifyScroll() }, 1500)
                                            }
                                        }
                                    }

                                    val viewerUrl = "https://appassets.androidplatform.net/assets/pdfjs/viewer.html"

                                    val fileAbsolutePath = file.absolutePath
                                    val fileUrl = when {
                                        fileAbsolutePath.startsWith(context.cacheDir.absolutePath) -> {
                                            val relativePath = fileAbsolutePath.substring(context.cacheDir.absolutePath.length)
                                            val encodedPath = relativePath.split('/').joinToString("/") { android.net.Uri.encode(it) }
                                            "https://appassets.androidplatform.net/cache$encodedPath"
                                        }
                                        fileAbsolutePath.startsWith(context.filesDir.absolutePath) -> {
                                            val relativePath = fileAbsolutePath.substring(context.filesDir.absolutePath.length)
                                            val encodedPath = relativePath.split('/').joinToString("/") { android.net.Uri.encode(it) }
                                            "https://appassets.androidplatform.net/internal$encodedPath"
                                        }
                                        else -> {
                                            val extDir = File(context.cacheDir, "ext_pdf").apply { if (!exists()) mkdirs() }
                                            val tempViewFile = File(extDir, "view_${file.name}")
                                            if (!tempViewFile.exists() || tempViewFile.length() != file.length()) {
                                                try { file.copyTo(tempViewFile, overwrite = true) } catch (_: Exception) {}
                                            }
                                            val target = if (tempViewFile.exists()) tempViewFile else file
                                            val relativePath = target.absolutePath.substring(context.cacheDir.absolutePath.length)
                                            val encodedPath = relativePath.split('/').joinToString("/") { android.net.Uri.encode(it) }
                                            "https://appassets.androidplatform.net/cache$encodedPath"
                                        }
                                    }

                                    val viewerStrings = listOf(
                                        "s_loading" to context.getString(R.string.loading),
                                        "s_error_title" to context.getString(R.string.pdf_viewer_error_title),
                                        "s_lib_failed" to context.getString(R.string.pdf_viewer_lib_load_failed),
                                        "s_page_loading" to context.getString(R.string.pdf_viewer_page_loading),
                                        "s_error" to context.getString(R.string.pdf_viewer_error)
                                    ).joinToString("&") { (key, value) -> "$key=${android.net.Uri.encode(value)}" }

                                    loadUrl("$viewerUrl?$viewerStrings&file=$fileUrl")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        DraggableScrollbar(
                            adapter = webViewScrollbarAdapter(webViewScrollState, webViewInstance)
                        )

                        PageNumberIndicator(
                            currentPage = currentPage,
                            totalPages = totalPages
                        )
                    }
                }

                WaitScreenOverlay(
                    show = isLoading && loadError == null,
                    message = stringResource(id = R.string.loading)
                )

                WaitScreenOverlay(
                    show = isPrinting,
                    message = stringResource(id = R.string.preparing)
                )
            }
        }
    }
}