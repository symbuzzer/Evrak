package com.avalibeyaz.evrak

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.avalibeyaz.evrak.ui.*
import com.avalibeyaz.evrak.ui.theme.EvrakTheme
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var currentIntent by mutableStateOf<Intent?>(null)
    private var showCelseIntegration by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        android.webkit.WebView.enableSlowWholeDocumentDraw()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntent = intent

        lifecycleScope.launch(Dispatchers.IO) {
            LibreOfficeManager.init(applicationContext)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            EvrakTheme(
                themeMode = themeMode
            ) {
                EvrakApp(
                    viewModel = viewModel,
                    intent = currentIntent,
                    showCelseIntegration = showCelseIntegration,
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isAdaletInstalled = com.avalibeyaz.evrak.ui.InstallUtils.isPackageInstalled(this, "com.adalet")
        val isSideloaded = com.avalibeyaz.evrak.ui.InstallUtils.isPackageSideloaded(this, "tr.gov.uyap.editor")
        showCelseIntegration = isAdaletInstalled && !isSideloaded
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent = intent
    }
}

@Composable
fun EvrakApp(viewModel: MainViewModel, intent: Intent?, showCelseIntegration: Boolean, onFinish: () -> Unit) {
    val navController = rememberNavController()
    val historyList by viewModel.historyList.collectAsState()
    val folderSelectionEnabled by viewModel.folderSelectionEnabled.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val fileSearchEnabled by viewModel.fileSearchEnabled.collectAsState()
    val dateFilterEnabled by viewModel.dateFilterEnabled.collectAsState()
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExperimentalDialog by remember { mutableStateOf(false) }
    
    val isExternalIntent = remember(intent) {
        intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND
    }

    NavHost(
        navController = navController, 
        startDestination = if (isExternalIntent) "intent_processor" else "history"
    ) {
        composable("history") {
            val context = androidx.compose.ui.platform.LocalContext.current
            MainScreen(
                historyList = historyList,
                onItemClick = { evrak ->
                    viewModel.updateEvrakTimestamp(evrak)
                    if (isArchiveType(evrak.path)) {
                        navController.navigate("viewer/${Uri.encode(evrak.path)}/${Uri.encode(evrak.name)}")
                    } else {
                        openDocumentTask(context, evrak.path, evrak.name)
                    }
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
                onPrintClick = { evrak, onConvertingChange ->
                    printFile(context, evrak.path, evrak.name, onConvertingChange)
                },
                onAboutClick = { showAboutDialog = true },
                onExperimentalClick = { showExperimentalDialog = true },
                onFilePicked = { uri ->
                    viewModel.openDocument(
                        uri = uri, 
                        resolver = context.contentResolver,
                        onError = { error ->
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                        },
                        onOpened = { evrak ->
                            if (isArchiveType(evrak.path)) {
                                navController.navigate("viewer/${Uri.encode(evrak.path)}/${Uri.encode(evrak.name)}")
                            } else {
                                openDocumentTask(context, evrak.path, evrak.name)
                            }
                        }
                    )
                },
                folderSelectionEnabled = folderSelectionEnabled,
                onDisableFolderSelection = { viewModel.disableFolderSelection() },
                selectedFilter = selectedFilter,
                onFilterChange = { viewModel.setFilter(it) },
                fileSearchEnabled = fileSearchEnabled,
                dateFilterEnabled = dateFilterEnabled,
                selectedDateFilter = selectedDateFilter,
                onDateFilterChange = { viewModel.setDateFilter(it) }
            )
        }
        composable("intent_processor") {
            WaitScreenOverlay(
                show = true,
                message = stringResource(id = R.string.loading)
            )
        }
        composable(
            route = "viewer/{filePath}/{displayName}?forceText={forceText}",
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
                navArgument("forceText") { 
                    type = NavType.BoolType
                    defaultValue = false 
                }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val displayName = backStackEntry.arguments?.getString("displayName") ?: ""
            val forceText = backStackEntry.arguments?.getBoolean("forceText") ?: false
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
                          filePath.endsWith(".gif", true) ||
                          filePath.endsWith(".webp", true) ||
                          filePath.endsWith(".bmp", true) ||
                          filePath.endsWith(".heic", true) ||
                          filePath.endsWith(".avif", true)
            
            val isOffice = filePath.endsWith(".docx", true) || 
                          filePath.endsWith(".doc", true)
            
            val isExcel = filePath.endsWith(".xlsx", true) ||
                          filePath.endsWith(".xls", true)

            val isPowerPoint = filePath.endsWith(".pptx", true) ||
                              filePath.endsWith(".ppt", true)

            
            val isUdf = filePath.endsWith(".udf", true)
            
            val isHtml = filePath.endsWith(".html", true) || 
                          filePath.endsWith(".htm", true)
            
            val isText = filePath.endsWith(".txt", ignoreCase = true)
            
            val isZip = filePath.endsWith(".zip", ignoreCase = true)
            
            val isEyp = filePath.endsWith(".eyp", ignoreCase = true)
            
            val onRenameSafe: (String) -> Unit = { newName ->
                viewModel.renameDocument(filePath, displayName, newName) { updated ->
                    navController.navigate("viewer/${Uri.encode(updated.path)}/${Uri.encode(updated.name)}?forceText=$forceText") {
                        popUpTo("viewer/${Uri.encode(filePath)}/${Uri.encode(displayName)}?forceText=$forceText") { inclusive = true }
                    }
                }
            }

            LaunchedEffect(filePath) {
                viewModel.ensureEvrak(filePath, displayName) { _ -> }
            }

            when {
                forceText || isText -> {
                    TextViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isPdf -> {
                    PdfViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isTiff -> {
                    TiffViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isImage -> {
                    ImageViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isOffice -> {
                    WordToPdfLoader(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isExcel -> {
                    OfficeToHtmlLoader(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isPowerPoint -> {
                    WordToPdfLoader(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isUdf -> {
                    UdfViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                isZip || isEyp -> {
                    ZipViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe,
                        onEntryClick = { entryPath, entryName ->
                            viewModel.openDocument(
                                uri = Uri.fromFile(File(entryPath)),
                                resolver = context.contentResolver,
                                onError = {
                                    if (isArchiveType(entryPath)) {
                                        navController.navigate("viewer/${Uri.encode(entryPath)}/${Uri.encode(entryName)}")
                                    } else {
                                        openDocumentTask(context, entryPath, entryName)
                                    }
                                },
                                onOpened = { evrak ->
                                    if (isArchiveType(evrak.path)) {
                                        navController.navigate("viewer/${Uri.encode(evrak.path)}/${Uri.encode(evrak.name)}")
                                    } else {
                                        openDocumentTask(context, evrak.path, evrak.name)
                                    }
                                }
                            )
                        }
                    )
                }
                isHtml -> {
                    HtmlViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onRenameClick = onRenameSafe
                    )
                }
                else -> {
                    UnsupportedViewerScreen(
                        filePath = filePath,
                        displayName = displayName,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) },
                        onTryAsTextClick = {
                            navController.navigate("viewer/${Uri.encode(filePath)}/${Uri.encode(displayName)}?forceText=true") {
                                popUpTo("viewer/${Uri.encode(filePath)}/${Uri.encode(displayName)}") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(showCelseIntegration = showCelseIntegration) { showAboutDialog = false }
    }

    if (showExperimentalDialog) {
        ExperimentalFeaturesDialog(
            viewModel = viewModel,
            onDismiss = { showExperimentalDialog = false }
        )
    }

    val activityContentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    LaunchedEffect(intent) {
        intent?.let {
            val isExternal = it.isExternalOpenIntent()

            if (it.getBooleanExtra("open_archive", false)) {
                val path = it.getStringExtra("file_path") ?: ""
                val name = it.getStringExtra("display_name") ?: ""
                if (path.isNotEmpty()) {
                    if (isExternal) {
                        context.showOpenedWithEvrakToast()
                    }
                    navController.navigate("viewer/${Uri.encode(path)}/${Uri.encode(name)}") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
                return@LaunchedEffect
            }

            if (it.getBooleanExtra("from_open_with", false)) {
                val path = it.getStringExtra("file_path") ?: ""
                val name = it.getStringExtra("display_name") ?: ""
                if (path.isNotEmpty()) {
                    if (isExternal) {
                        context.showOpenedWithEvrakToast()
                    }
                    if (isArchiveType(path)) {
                        navController.navigate("viewer/${Uri.encode(path)}/${Uri.encode(name)}") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    } else {
                        openDocumentTask(context, path, name)
                        if (navController.currentDestination?.route == "intent_processor") {
                            navController.navigate("history") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
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
                
                viewModel.openDocument(
                    uri = fileUri, 
                    resolver = activityContentResolver,
                    onError = { error ->
                        android.widget.Toast.makeText(appContext, error, android.widget.Toast.LENGTH_LONG).show()
                        if (navController.currentDestination?.route == "intent_processor") {
                            navController.navigate("history") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    },
                    onOpened = { evrak ->
                        if (isExternal) {
                            context.showOpenedWithEvrakToast()
                        }
                        if (isArchiveType(evrak.path)) {
                            navController.navigate("viewer/${Uri.encode(evrak.path)}/${Uri.encode(evrak.name)}") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        } else {
                            openDocumentTask(context, evrak.path, evrak.name)
                            if (navController.currentDestination?.route == "intent_processor") {
                                navController.navigate("history") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            }
                        }
                    }
                )
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
            filePath.endsWith(".gif", true) ||
            filePath.endsWith(".webp", true) ||
            filePath.endsWith(".bmp", true) ||
            filePath.endsWith(".heic", true) ||
            filePath.endsWith(".avif", true)

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
                var input: java.io.FileInputStream? = null
                var output: java.io.FileOutputStream? = null
                try {
                    input = java.io.FileInputStream(file)
                    output = java.io.FileOutputStream(destination?.fileDescriptor)
                    input.copyTo(output)
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback?.onWriteFailed(e.message)
                } finally {
                    try { input?.close() } catch (_: Exception) {}
                }
            }
        }, 
        null
    )
}
