package com.avalibeyaz.evrak.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordToPdfLoader(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var tempPdfPath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConverting by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        loadError = context.getString(R.string.error_file_not_found)
                    }
                    return@withContext
                }

                val tempPdf = File(context.cacheDir, "view_temp_${System.currentTimeMillis()}.pdf")
                val activity = context.findActivity()
                val success = if (activity != null) {
                    LibreOfficeManager.convertToPdf(file, tempPdf, activity)
                } else {
                    false
                }
                
                if (success && tempPdf.exists()) {
                    tempPdfPath = tempPdf.absolutePath
                } else {
                    withContext(Dispatchers.Main) {
                        loadError = context.getString(R.string.error_conversion_failed, "LibreOffice engine error")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadError = e.localizedMessage ?: context.getString(R.string.error_unknown)
                }
            } finally {
                isLoading = false
            }
        }
    }

    val saveWordLauncher = rememberLauncherForActivityResult(
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
                    tempPdfPath?.let { path ->
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            File(path).inputStream().use { input -> input.copyTo(output) }
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

    if (isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { MarqueeTitle(title = displayName) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    } else if (tempPdfPath != null) {
        PdfViewerScreen(
            filePath = tempPdfPath!!,
            displayName = displayName,
            onBackClick = onBackClick,
            onShareClick = { showFormatDialog = "share" },
            onSaveClick = { showFormatDialog = "save" }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { MarqueeTitle(title = displayName) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = loadError ?: stringResource(id = R.string.error_unknown), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onBackClick, modifier = Modifier.padding(top = 16.dp)) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }

    if (showFormatDialog != null) {
        val ext = if (filePath.endsWith(".docx", true)) "DOCX" else "DOC"
        FormatSelectionDialog(
            extension = ext,
            onDismiss = { showFormatDialog = null },
            onFormatSelected = { usePdf ->
                if (showFormatDialog == "save") {
                    if (usePdf) {
                        val newName = displayName.substringBeforeLast(".") + ".pdf"
                        savePdfLauncher.launch(newName)
                    } else {
                        saveWordLauncher.launch(displayName)
                    }
                } else {
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            tempPdfPath?.let { path ->
                                DocumentConverter.shareFile(context, File(path), "application/pdf")
                            }
                        } else {
                            onShareClick()
                        }
                    }
                }
            }
        )
    }

    if (isConverting) {
        Box(
            modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.converting), color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
