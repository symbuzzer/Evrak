package com.avalibeyaz.evrak.ui

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
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
    saveMimeType: String = "application/pdf"
) {
    val context = LocalContext.current
    
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    MarqueeTitle(
                        title = displayName
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
                                Text(stringResource(id = R.string.save))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            if (onSaveClick != null) {
                                onSaveClick()
                            } else {
                                saveLauncher.launch(saveDisplayName)
                            }
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
                        IconButton(onClick = onShareClick) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val file = File(filePath)
        
        val assetLoader = remember {
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

        var jsMissing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            try {
                val jsExists = try { context.assets.open("pdfjs/pdf.min.js").close(); true } catch (e: Exception) { false }
                val mjsExists = try { context.assets.open("pdfjs/pdf.min.mjs").close(); true } catch (e: Exception) { false }
                
                if (!jsExists && !mjsExists) {
                    Log.e("PdfViewer", "PDF.js assets missing in assets/pdfjs/")
                    jsMissing = true
                } else {
                    Log.d("PdfViewer", "PDF.js assets found (js: $jsExists, mjs: $mjsExists)")
                }
            } catch (_: Exception) {
                jsMissing = true
            }
        }

        if (jsMissing) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "PDF.js kütüphanesi eksik!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lütfen pdf.min.mjs (veya .js) ve pdf.worker.min.mjs dosyalarını assets/pdfjs/ klasörüne ekleyin.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) {
                        Text("Geri Dön")
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
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
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                Log.d("PdfViewerJS", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
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
                                Log.d("PdfViewer", "Page finished loading: $url")
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                Log.e("PdfViewer", "Error loading ${request?.url}: ${error?.description}")
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
                                "https://appassets.androidplatform.net/internal/${android.net.Uri.encode(file.name)}"
                            }
                        }
                        
                        loadUrl("$viewerUrl?file=$fileUrl")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}
