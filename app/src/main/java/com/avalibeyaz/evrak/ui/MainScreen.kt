package com.avalibeyaz.evrak.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.avalibeyaz.evrak.BuildConfig
import com.avalibeyaz.evrak.R
import com.avalibeyaz.evrak.data.Evrak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    historyList: List<Evrak>,
    onItemClick: (Evrak) -> Unit,
    onDeleteClick: (Evrak) -> Unit,
    onRenameClick: (Evrak, String) -> Unit,
    onDeleteAllClick: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateClick: () -> Unit,
    onShareAppClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedEvrak by remember { mutableStateOf<Evrak?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Evrak?>(null) }
    var showRenameDialog by remember { mutableStateOf<Evrak?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf<String?>(null) }
    var conversionError by remember { mutableStateOf<String?>(null) }

    // Save launchers
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            selectedEvrak?.let { evrak ->
                scope.launch(Dispatchers.IO) {
                    isConverting = true
                    try {
                        val tempPdf = File(context.cacheDir, "temp_main_convert.pdf")
                        val result = DocumentConverter.convert(File(evrak.path), tempPdf)
                        if (result is DocumentConverter.ConversionResult.Success) {
                            context.contentResolver.openOutputStream(destUri)?.use { output ->
                                tempPdf.inputStream().use { input -> input.copyTo(output) }
                            }
                        } else if (result is DocumentConverter.ConversionResult.Error) {
                            withContext(Dispatchers.Main) {
                                conversionError = context.getString(R.string.error_conversion_failed, result.message)
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
    }

    val saveOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destUri ->
            selectedEvrak?.let { evrak ->
                scope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            File(evrak.path).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(text = "${stringResource(id = R.string.app_name)} v${BuildConfig.VERSION_NAME}") 
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.share_app))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onShareAppClick) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share_app))
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.update))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onUpdateClick) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = stringResource(id = R.string.update))
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.about))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onAboutClick) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(id = R.string.about))
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    onRefresh()
                    delay(1000) // Minimum delay for smooth animation
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(id = R.string.no_history))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.history),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            
                            TextButton(
                                onClick = { showDeleteAllConfirm = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.delete_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    items(historyList) { evrak ->
                        EvrakItem(
                            evrak = evrak, 
                            onClick = { onItemClick(evrak) },
                            onLongClick = {
                                selectedEvrak = evrak
                                showSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSheet && (selectedEvrak != null)) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = selectedEvrak!!.name,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider()
                
                OptionItem(
                    icon = Icons.Default.Share,
                    label = stringResource(id = R.string.send),
                    onClick = {
                        val path = selectedEvrak!!.path
                        val isConvertible = path.endsWith(".udf", true) || 
                                           path.endsWith(".tiff", true) || 
                                           path.endsWith(".tif", true)
                        
                        if (isConvertible) {
                            showFormatDialog = "share"
                            showSheet = false
                        } else {
                            showSheet = false
                            shareFile(context, selectedEvrak!!)
                        }
                    }
                )
                
                OptionItem(
                    icon = Icons.Default.Save,
                    label = stringResource(id = R.string.save),
                    onClick = {
                        val path = selectedEvrak!!.path
                        val isConvertible = path.endsWith(".udf", true) || 
                                           path.endsWith(".tiff", true) || 
                                           path.endsWith(".tif", true)
                        
                        if (isConvertible) {
                            showFormatDialog = "save"
                            showSheet = false
                        } else {
                            showSheet = false
                            saveOriginalLauncher.launch(selectedEvrak!!.name)
                        }
                    }
                )

                OptionItem(
                    icon = Icons.Default.Edit,
                    label = stringResource(id = R.string.rename),
                    onClick = {
                        showSheet = false
                        showRenameDialog = selectedEvrak
                    }
                )
                
                OptionItem(
                    icon = Icons.Default.Delete,
                    label = stringResource(id = R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        showSheet = false
                        showDeleteConfirm = selectedEvrak
                    }
                )
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(text = stringResource(id = R.string.delete)) },
            text = { Text(text = stringResource(id = R.string.confirm_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showRenameDialog != null) {
        var newName by remember { mutableStateOf(showRenameDialog!!.name.substringBeforeLast(".")) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(text = stringResource(id = R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(text = stringResource(id = R.string.rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenameClick(showRenameDialog!!, newName)
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text(text = stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(text = stringResource(id = R.string.delete_all)) },
            text = { Text(text = stringResource(id = R.string.confirm_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllClick()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showFormatDialog != null && selectedEvrak != null) {
        val ext = selectedEvrak!!.path.substringAfterLast(".").uppercase()
        FormatSelectionDialog(
            extension = ext,
            onDismiss = { showFormatDialog = null },
            onFormatSelected = { usePdf ->
                if (showFormatDialog == "save") {
                    if (usePdf) {
                        val newName = selectedEvrak!!.name.substringBeforeLast(".") + ".pdf"
                        savePdfLauncher.launch(newName)
                    } else {
                        saveOriginalLauncher.launch(selectedEvrak!!.name)
                    }
                } else {
                    // share
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            isConverting = true
                            try {
                                val pdfName = selectedEvrak!!.name.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convert(File(selectedEvrak!!.path), tempPdf)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    shareConvertedFile(context, tempPdf, "application/pdf")
                                }
                            } finally {
                                isConverting = false
                            }
                        } else {
                            shareFile(context, selectedEvrak!!)
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

    if (conversionError != null) {
        AlertDialog(
            onDismissRequest = { conversionError = null },
            title = { Text(text = stringResource(id = R.string.error)) },
            text = { Text(text = conversionError!!) },
            confirmButton = {
                TextButton(onClick = { conversionError = null }) {
                    Text(text = stringResource(id = R.string.ok))
                }
            }
        )
    }
}

private fun shareFile(context: android.content.Context, evrak: Evrak) {
    val file = File(evrak.path)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val mimeType = when {
        evrak.path.endsWith(".pdf", true) -> "application/pdf"
        evrak.path.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        evrak.path.endsWith(".doc", true) -> "application/msword"
        evrak.path.endsWith(".png", true) -> "image/png"
        evrak.path.endsWith(".jpg", true) || evrak.path.endsWith(".jpeg", true) -> "image/jpeg"
        evrak.path.endsWith(".gif", true) -> "image/gif"
        evrak.path.endsWith(".udf", true) -> "application/x-udf"
        evrak.path.endsWith(".tiff", true) || evrak.path.endsWith(".tif", true) -> "image/tiff"
        else -> "application/octet-stream"
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}

private fun shareConvertedFile(context: android.content.Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EvrakItem(evrak: Evrak, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = evrak.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.getDefault())
                    .format(Date(evrak.dateOpened)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun OptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { Text(text = label, color = color) },
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
