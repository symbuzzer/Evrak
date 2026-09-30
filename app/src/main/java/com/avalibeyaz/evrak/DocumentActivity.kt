package com.avalibeyaz.evrak

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.avalibeyaz.evrak.ui.*
import com.avalibeyaz.evrak.ui.theme.EvrakTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DocumentActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        android.webkit.WebView.enableSlowWholeDocumentDraw()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            LibreOfficeManager.init(applicationContext)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val filePathExtra = intent.getStringExtra("file_path") ?: ""
        val displayNameExtra = intent.getStringExtra("display_name") ?: ""
        val uriExtra: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }

        if (displayNameExtra.isNotEmpty()) {
            setDocumentTaskDescription(displayNameExtra)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            var currentFilePath by remember { mutableStateOf(filePathExtra) }
            var currentDisplayName by remember { mutableStateOf(displayNameExtra) }
            var forceTextState by remember { mutableStateOf(false) }
            var isLoading by remember { mutableStateOf(currentFilePath.isEmpty() && uriExtra != null) }

            LaunchedEffect(intent) {
                if (currentFilePath.isEmpty() && uriExtra != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uriExtra,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    viewModel.openDocument(
                        uri = uriExtra,
                        resolver = contentResolver,
                        onError = { error ->
                            Toast.makeText(applicationContext, error, Toast.LENGTH_LONG).show()
                            isLoading = false
                            if (currentFilePath.isEmpty()) {
                                finish()
                            }
                        },
                        onOpened = { evrak ->
                            currentFilePath = evrak.path
                            currentDisplayName = evrak.name
                            setDocumentTaskDescription(evrak.name)
                            isLoading = false
                        }
                    )
                } else if (currentFilePath.isNotEmpty()) {
                    setDocumentTaskDescription(currentDisplayName)
                    isLoading = false
                    viewModel.ensureEvrak(currentFilePath, currentDisplayName) { evrak ->
                        currentFilePath = evrak.path
                        currentDisplayName = evrak.name
                    }
                }
            }

            EvrakTheme(themeMode = themeMode) {
                if (isLoading || currentFilePath.isEmpty()) {
                    WaitScreenOverlay(
                        show = true,
                        message = stringResource(id = R.string.loading)
                    )
                } else {
                    DocumentViewerContent(
                        filePath = currentFilePath,
                        displayName = currentDisplayName,
                        forceText = forceTextState,
                        onBack = { finish() },
                        onShare = { shareFile(currentFilePath) },
                        onRename = { newName ->
                            viewModel.renameDocument(currentFilePath, currentDisplayName, newName) { updated ->
                                currentFilePath = updated.path
                                currentDisplayName = updated.name
                                setDocumentTaskDescription(updated.name)
                            }
                        },
                        onTryAsText = { forceTextState = true }
                    )
                }
            }
        }
    }

    private fun setDocumentTaskDescription(title: String) {
        val taskTitle = "${getString(R.string.app_name)} - $title"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(
                ActivityManager.TaskDescription.Builder()
                    .setLabel(taskTitle)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(taskTitle))
        }
    }

    private fun shareFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(filePath)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }
}

@Composable
private fun DocumentViewerContent(
    filePath: String,
    displayName: String,
    forceText: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    onTryAsText: () -> Unit
) {
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

    when {
        forceText || isText -> {
            TextViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isPdf -> {
            PdfViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isTiff -> {
            TiffViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isImage -> {
            ImageViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isOffice -> {
            WordToPdfLoader(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isExcel -> {
            OfficeToHtmlLoader(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isPowerPoint -> {
            WordToPdfLoader(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isUdf -> {
            UdfViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        isHtml -> {
            HtmlViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onRenameClick = onRename
            )
        }
        else -> {
            UnsupportedViewerScreen(
                filePath = filePath,
                displayName = displayName,
                onBackClick = onBack,
                onShareClick = onShare,
                onTryAsTextClick = onTryAsText
            )
        }
    }
}
