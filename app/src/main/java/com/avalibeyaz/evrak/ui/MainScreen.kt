package com.avalibeyaz.evrak.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
    onShareAppClick: () -> Unit,
    onPrintClick: (Evrak, (Boolean) -> Unit) -> Unit,
    onAboutClick: () -> Unit,
    onFilePicked: (Uri) -> Unit,
    folderSelectionEnabled: Boolean,
    onDisableFolderSelection: () -> Unit
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

    var selectedFilter by remember { mutableStateOf(EvrakFilter.ALL) }
    var initialUri by remember { mutableStateOf<Uri?>(null) }
    var showFolderMenu by remember { mutableStateOf(false) }

    val availableFilters = remember(historyList) {
        val filters = mutableListOf(EvrakFilter.ALL)
        if (historyList.any { it.path.endsWith(".udf", true) }) filters.add(EvrakFilter.UDF)
        if (historyList.any { it.path.endsWith(".pdf", true) }) filters.add(EvrakFilter.PDF)
        if (historyList.any { it.path.endsWith(".tif", true) || it.path.endsWith(".tiff", true) }) filters.add(EvrakFilter.TIFF)
        if (historyList.any { it.path.endsWith(".doc", true) || it.path.endsWith(".docx", true) }) filters.add(EvrakFilter.WORD)
        if (historyList.any {
                val path = it.path.lowercase()
                path.endsWith(".html") || path.endsWith(".htm") ||
                path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".png")
            }) filters.add(EvrakFilter.OTHER)
        filters
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                initialUri?.let {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
                }
                return intent
            }
        }
    ) { uri ->
        uri?.let { onFilePicked(it) }
    }

    fun launchFilePicker(folderUri: Uri? = null) {
        val mimeTypes = arrayOf(
            "application/pdf",
            "image/tiff",
            "image/jpeg",
            "image/png",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/html",
            "application/octet-stream"
        )
        initialUri = folderUri
        try {
            openDocumentLauncher.launch(mimeTypes)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.error_folder_selection_not_supported), Toast.LENGTH_LONG).show()
            onDisableFolderSelection()
            initialUri = null
            openDocumentLauncher.launch(mimeTypes)
        }
    }

    LaunchedEffect(availableFilters) {
        if (selectedFilter !in availableFilters) {
            selectedFilter = EvrakFilter.ALL
        }
    }

    val filteredList = remember(historyList, selectedFilter) {
        when (selectedFilter) {
            EvrakFilter.ALL -> historyList
            EvrakFilter.UDF -> historyList.filter { it.path.endsWith(".udf", true) }
            EvrakFilter.PDF -> historyList.filter { it.path.endsWith(".pdf", true) }
            EvrakFilter.TIFF -> historyList.filter { it.path.endsWith(".tif", true) || it.path.endsWith(".tiff", true) }
            EvrakFilter.WORD -> historyList.filter { it.path.endsWith(".doc", true) || it.path.endsWith(".docx", true) }
            EvrakFilter.OTHER -> historyList.filter {
                val path = it.path.lowercase()
                path.endsWith(".html") || path.endsWith(".htm") ||
                path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".png")
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            selectedEvrak?.let { evrak ->
                scope.launch(Dispatchers.IO) {
                    isConverting = true
                    try {
                        val tempPdf = File(context.cacheDir, "temp_main_convert.pdf")
                        val result = DocumentConverter.convert(File(evrak.path), tempPdf, context)
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
                    Text(text = stringResource(id = R.string.app_name)) 
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.open_file))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        Box {
                            IconButton(onClick = { 
                                if (folderSelectionEnabled) {
                                    showFolderMenu = true
                                } else {
                                    launchFilePicker()
                                }
                            }) {
                                Icon(
                                    Icons.Default.FileOpen,
                                    contentDescription = stringResource(id = R.string.open_file),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showFolderMenu,
                                onDismissRequest = { showFolderMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.downloads)) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showFolderMenu = false
                                        launchFilePicker(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.documents)) },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                    onClick = {
                                        showFolderMenu = false
                                        launchFilePicker(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments"))
                                    }
                                )
                            }
                        }
                    }
                    if (historyList.isNotEmpty()) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(id = R.string.delete))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showDeleteAllConfirm = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(id = R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
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
                    delay(1000)
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

                    if (historyList.isNotEmpty() && availableFilters.size > 1) {
                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(availableFilters) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(text = stringResource(id = filter.labelResId)) }
                                    )
                                }
                            }
                        }
                    }
                    items(filteredList, key = { it.id }) { evrak ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    selectedEvrak = evrak
                                    showSheet = true
                                    false
                                } else {
                                    true
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val color = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                                val alignment = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                                val icon = Icons.Default.Menu

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, MaterialTheme.shapes.medium)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = alignment
                                ) {
                                    if (direction != SwipeToDismissBoxValue.Settled) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            content = {
                                EvrakItem(
                                    evrak = evrak,
                                    onClick = { onItemClick(evrak) },
                                    onLongClick = {
                                        selectedEvrak = evrak
                                        showSheet = true
                                    }
                                )
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
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = selectedEvrak!!.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                OptionItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = stringResource(id = R.string.open_with),
                    onClick = {
                        showSheet = false
                        openFileWith(context, selectedEvrak!!)
                    }
                )
                
                OptionItem(
                    icon = Icons.Default.Share,
                    label = stringResource(id = R.string.share),
                    onClick = {
                        val path = selectedEvrak!!.path
                        val isConvertible = path.endsWith(".udf", true) || 
                                           path.endsWith(".tiff", true) || 
                                           path.endsWith(".tif", true) ||
                                           path.endsWith(".docx", true) ||
                                           path.endsWith(".doc", true) ||
                                           path.endsWith(".html", true) ||
                                           path.endsWith(".htm", true)
                        
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
                                           path.endsWith(".tif", true) ||
                                           path.endsWith(".docx", true) ||
                                           path.endsWith(".doc", true) ||
                                           path.endsWith(".html", true) ||
                                           path.endsWith(".htm", true)
                        
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
                    icon = Icons.Default.Print,
                    label = stringResource(id = R.string.print),
                    onClick = {
                        showSheet = false
                        onPrintClick(selectedEvrak!!) { converting ->
                            isConverting = converting
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
            title = { Text(text = stringResource(id = R.string.delete)) },
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
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            isConverting = true
                            try {
                                val pdfName = selectedEvrak!!.name.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convert(File(selectedEvrak!!.path), tempPdf, context)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    DocumentConverter.shareFile(context, tempPdf, "application/pdf")
                                } else if (result is DocumentConverter.ConversionResult.Error) {
                                    withContext(Dispatchers.Main) {
                                        conversionError = context.getString(R.string.error_conversion_failed, result.message)
                                    }
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

    ConversionOverlay(isConverting = isConverting)

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
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = getMimeType(evrak.path)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}

private fun openFileWith(context: android.content.Context, evrak: Evrak) {
    val file = File(evrak.path)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, getMimeType(evrak.path))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra("from_open_with", true)
        putExtra("file_path", evrak.path)
        putExtra("display_name", evrak.name)
    }

    val packageManager = context.packageManager
    val activities = packageManager.queryIntentActivities(intent, 0)
    val isOtherAppAvailable = activities.any { it.activityInfo.packageName != context.packageName }

    if (!isOtherAppAvailable) {
        Toast.makeText(context, context.getString(R.string.no_other_apps), Toast.LENGTH_SHORT).show()
    }

    context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)))
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EvrakItem(evrak: Evrak, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val dateText = remember(evrak.dateOpened) {
        val date = Date(evrak.dateOpened)
        val dateFormat = DateFormat.getDateFormat(context)
        
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
        
        "${dateFormat.format(date)} - ${timeFormat.format(date)}"
    }
    
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
                text = dateText,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = color, 
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label, 
            color = color, 
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

enum class EvrakFilter(val labelResId: Int) {
    ALL(R.string.filter_all),
    UDF(R.string.filter_udf),
    PDF(R.string.filter_pdf),
    TIFF(R.string.filter_tiff),
    WORD(R.string.filter_word),
    OTHER(R.string.filter_other)
}
