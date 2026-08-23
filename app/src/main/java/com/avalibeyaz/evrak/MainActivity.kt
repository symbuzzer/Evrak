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
import com.avalibeyaz.evrak.ui.WordViewerScreen
import com.avalibeyaz.evrak.ui.UdfViewerScreen
import com.avalibeyaz.evrak.ui.HtmlViewerScreen
import com.avalibeyaz.evrak.ui.UnsupportedViewerScreen
import com.avalibeyaz.evrak.ui.theme.EvrakTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var currentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
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
                onUpdateClick = {
                    val url = "https://symbuzzer.github.io/evrak/index.htm?ver=${BuildConfig.VERSION_NAME}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                onShareAppClick = {
                    val shareText = context.getString(R.string.share_app_text)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, shareAppLabel))
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
            
            // Safety check for rapid back clicks
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
                    WordViewerScreen(
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

    // Handle Incoming Intent (Samsung/Global)
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
                // Attempt to take persistable permission
                try {
                    activityContentResolver.takePersistableUriPermission(
                        fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Ignore failure if not persistable
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
    val mimeType = when {
        filePath.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        filePath.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        filePath.endsWith(".doc", ignoreCase = true) -> "application/msword"
        filePath.endsWith(".tif", ignoreCase = true) || filePath.endsWith(".tiff", ignoreCase = true) -> "image/tiff"
        filePath.endsWith(".png", true) -> "image/png"
        filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
        filePath.endsWith(".gif", true) -> "image/gif"
        filePath.endsWith(".udf", true) -> "application/x-udf"
        filePath.endsWith(".html", true) || filePath.endsWith(".htm", true) -> "text/html"
        else -> "application/octet-stream"
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}
