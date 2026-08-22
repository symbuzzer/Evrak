package com.avalibeyaz.evrak

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
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
                EvrakApp(viewModel, currentIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent = intent
    }
}

@Composable
fun EvrakApp(viewModel: MainViewModel, intent: Intent?) {
    val navController = rememberNavController()
    val historyList by viewModel.historyList.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = "history") {
        composable("history") {
            val context = androidx.compose.ui.platform.LocalContext.current
            MainScreen(
                historyList = historyList,
                onItemClick = { evrak ->
                    navController.navigate("viewer/${Uri.encode(evrak.path)}")
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
                onAboutClick = { showAboutDialog = true }
            )
        }
        composable("viewer/{filePath}") { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Safety check for rapid back clicks
            val onBackSafe = {
                if (backStackEntry.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                    navController.popBackStack()
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
            
            when {
                isPdf -> {
                    PdfViewerScreen(
                        filePath = filePath,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isTiff -> {
                    TiffViewerScreen(
                        filePath = filePath,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isImage -> {
                    ImageViewerScreen(
                        filePath = filePath,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                isWord -> {
                    WordViewerScreen(
                        filePath = filePath,
                        onBackClick = onBackSafe,
                        onShareClick = { shareFile(context, filePath) }
                    )
                }
                else -> {
                    UnsupportedViewerScreen(
                        filePath = filePath,
                        onBackClick = onBackSafe
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    // Handle Incoming Intent (Samsung/Global)
    val activityContentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    LaunchedEffect(intent) {
        intent?.let {
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
                } catch (e: Exception) {
                    // Ignore failure if not persistable
                }
                
                viewModel.openDocument(fileUri, activityContentResolver) { evrak ->
                    navController.navigate("viewer/${Uri.encode(evrak.path)}") {
                        popUpTo("history")
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
        else -> "application/octet-stream"
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}
