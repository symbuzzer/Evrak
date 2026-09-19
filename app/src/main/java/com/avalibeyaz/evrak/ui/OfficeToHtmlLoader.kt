package com.avalibeyaz.evrak.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun OfficeToHtmlLoader(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRenameClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var tempHtmlPath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
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

                val tempDir = File(context.filesDir, "temp_h").apply { if (!exists()) mkdirs() }
                val tempHtml = File(tempDir, "view_temp_${System.currentTimeMillis()}.html")
                val result = DocumentConverter.convertToHtml(file, tempHtml, context)
                
                if (result is DocumentConverter.ConversionResult.Success && tempHtml.exists()) {
                    withContext(Dispatchers.Main) {
                        tempHtmlPath = tempHtml.absolutePath
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val msg = if (result is DocumentConverter.ConversionResult.Error) result.message else "Conversion failed"
                        loadError = msg
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadError = e.localizedMessage ?: context.getString(R.string.error_unknown)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val saveOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        File(filePath).inputStream().use { input -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { MarqueeTitle(title = displayName, onRenameClick = onRenameClick) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                WaitScreenOverlay(
                    show = true,
                    message = stringResource(id = R.string.loading)
                )
            }
        }
    } else if (tempHtmlPath != null) {
        HtmlViewerScreen(
            filePath = tempHtmlPath!!,
            displayName = displayName,
            onBackClick = onBackClick,
            onShareClick = onShareClick,
            onSaveClick = { saveOriginalLauncher.launch(displayName) },
            originalExtension = filePath.substringAfterLast(".").uppercase(),
            originalFilePath = filePath,
            onRenameClick = onRenameClick
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { MarqueeTitle(title = displayName, onRenameClick = onRenameClick) },
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
}
