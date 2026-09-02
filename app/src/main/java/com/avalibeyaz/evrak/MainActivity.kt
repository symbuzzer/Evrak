package com.avalibeyaz.evrak

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avalibeyaz.evrak.ui.AboutDialog
import com.avalibeyaz.evrak.ui.MainScreen
import com.avalibeyaz.evrak.ui.ImageViewerScreen
import com.avalibeyaz.evrak.ui.TiffViewerScreen
import com.avalibeyaz.evrak.ui.PdfViewerScreen
import com.avalibeyaz.evrak.ui.WordToPdfLoader
import com.avalibeyaz.evrak.ui.getMimeType
import com.avalibeyaz.evrak.ui.UdfViewerScreen
import com.avalibeyaz.evrak.ui.HtmlViewerScreen
import com.avalibeyaz.evrak.ui.UnsupportedViewerScreen
import com.avalibeyaz.evrak.ui.theme.EvrakTheme
import com.avalibeyaz.evrak.ui.DocumentConverter
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var currentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        android.webkit.WebView.enableSlowWholeDocumentDraw()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntent = intent
        setContent {
            EvrakTheme {
                EvrakApp(viewModel, currentIntent, onFinish = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent = intent
    }
}

@Composable
fun EvrakApp(viewModel: MainViewModel, intent: Intent?, onFinish: () -> Unit) {
    val navController = rememberNavController()
    val historyList by viewModel.historyList.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    
    val isExternalIntent = remember(intent) {
        intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND
    }

    NavHost(
        navController = navController, 
        startDestination = if (isExternalIntent) "intent_processor" else "history"
    ) {
        composable("history") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val shareAppLabel = stringResource(id = R.string.share_app)
            MainScreen(
                historyList = historyList,
                onItemClick = { evrak ->
                    navController.navigate("viewer/${Uri.encode(evrak.path)}/${Uri.encode(evrak.name)}")
                },
                onDeleteClick = { evrak ->
                    viewModel.deleteEvrak(evrak)
                },
                onRenameClick = { evrak, newName ->
                    viewModel.renameEvrak(evrak, newName)
                },
                onDeleteAllClick = {
                    viewModel.deleteAllEvrak()
                },
                onRefresh = {
                    viewModel.refreshHistory()
                },
                onShareAppClick = {
                    val shareText = context.getString(R.string.share_app_text)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, shareAppLabel))
                },
                onPrintClick = { evrak, onConvertingChange ->
                    printFile(context, evrak.path, evrak.name, onConvertingChange)
                },
                onAboutClick = { showAboutDialog = true }
            )
        }
        composable("intent_processor") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        composable("viewer/{filePath}/{displayName}") { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val displayName = backStackEntry.arguments?.getString("displayName") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            
            val onBackSafe = {
                if (backStackEntry.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                    if (!navController.popBackStack()) {
                        onFinish()
                    }
                }
            }
            
            val isPdf = filePath.endsWith(".pdf", ignoreCase = true)
            val isTiff = filePath.endsWith(".tif", ignoreCase = true) || 
                           filePath.endsWith(".tiff", ignoreCase = true)
            val isImage = filePath.endsWith(".png", true) || 
                          filePath.endsWith(".jpg", true) || 
                          filePath.endsWith(".jpeg", true) || 
                          filePath.endsWith(".gif", true)
            
            val isWord = filePath.endsWith(".docx", true) || 
                          filePath.endsWith(".doc", true)
            
            val isUdf = filePath.endsWith(".udf", true)
            
            val isHtml = filePath.endsWith(".html", true) || 
                          filePath.endsWith(".htm", true)
            
            when {
                isPdf -> {
                    PdfViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isTiff -> {
                    TiffViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isImage -> {
                    ImageViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isWord -> {
                    WordToPdfLoader(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isUdf -> {
                    UdfViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isHtml -> {
                    HtmlViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                else -> {
                    UnsupportedViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog { showAboutDialog = false }
    }

    val activityContentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    LaunchedEffect(intent) {
        intent?.let {
            if (it.getBooleanExtra("from_open_with", false)) {
                val path = it.getStringExtra("file_path") ?: ""
                val name = it.getStringExtra("display_name") ?: ""
                if (path.isNotEmpty()) {
                    navController.navigate("viewer/${Uri.encode(path)}/${Uri.encode(name)}") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
                return@LaunchedEffect
            }

            val uri: Uri? = when (it.action) {
                Intent.ACTION_VIEW -> it.data
                Intent.ACTION_SEND -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                }
                else -> null
            }

            uri?.let { fileUri ->
                try {
                    activityContentResolver.takePersistableUriPermission(
                        fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                
                viewModel.openDocument(fileUri, activityContentResolver) { evrak ->
                    navController.navigate("viewer/${Uri.encode(evrak.path)}/${Uri.encode(evrak.name)}") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, filePath: String) {
    val file = File(filePath)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = getMimeType(filePath)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}

private fun printFile(
    context: android.content.Context, 
    filePath: String, 
    displayName: String,
    onConvertingChange: (Boolean) -> Unit = {}
) {
    val file = File(filePath)
    if (!file.exists()) return

    val isImage = filePath.endsWith(".jpg", true) ||
            filePath.endsWith(".jpeg", true) ||
            filePath.endsWith(".png", true) ||
            filePath.endsWith(".gif", true)

    if (filePath.endsWith(".pdf", true)) {
        doPrint(context, file, displayName)
    } else if (isImage) {
        doPrintImage(context, file, displayName)
    } else {
        if (context is ComponentActivity) {
            context.lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { onConvertingChange(true) }
                try {
                    val tempPdf = File(context.cacheDir, "print_temp_${System.currentTimeMillis()}.pdf")
                    val result = DocumentConverter.convert(file, tempPdf, context)
                    if (result is DocumentConverter.ConversionResult.Success) {
                        withContext(Dispatchers.Main) {
                            doPrint(context, tempPdf, displayName)
                        }
                    } else if (result is DocumentConverter.ConversionResult.Error) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, context.getString(R.string.error_conversion_failed, result.message), android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) { onConvertingChange(false) }
                }
            }
        }
    }
}

private fun doPrintImage(context: android.content.Context, file: File, displayName: String) {
    val printHelper = PrintHelper(context)
    printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
    if (bitmap != null) {
        printHelper.printBitmap(displayName, bitmap)
    }
}

private fun doPrint(context: android.content.Context, file: File, displayName: String) {
    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
    val jobName = "${context.getString(R.string.app_name)} - $displayName"

    val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)

    printManager.print(
        jobName, 
        object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
            oldAttributes: android.print.PrintAttributes?,
            newAttributes: android.print.PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: android.os.Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }

            val info = android.print.PrintDocumentInfo.Builder(displayName)
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: android.os.ParcelFileDescriptor?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            try {
                val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                val output = java.io.FileOutputStream(destination?.fileDescriptor)
                input.copyTo(output)
                callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.onWriteFailed(e.message)
            }
        }
    }, null)
}
