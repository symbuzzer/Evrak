package com.avalibeyaz.evrak.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import net.lingala.zip4j.ZipFile

private val SUPPORTED_EXTENSIONS = setOf(
    ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".tiff", ".tif",
    ".png", ".jpg", ".jpeg", ".gif", ".udf", ".html", ".htm", ".txt", ".zip", ".eyp",
    ".webp", ".bmp", ".heic", ".avif"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ZipViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRenameClick: (String) -> Unit = {},
    onEntryClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentCharset = remember(filePath) {
        ZipUtils.determineCharset(filePath)
    }
    
    var zipEntries by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var selectedEntry by remember { mutableStateOf<String?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    File(filePath).inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            isExtracting = true
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    
                    ZipUtils.extractZip(context, filePath, treeUri, currentCharset)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.extract_success), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.extract_error), Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isExtracting = false
                    }
                }
            }
        }
    }

    fun loadZipEntries() {
        try {
            val file = File(filePath)
            if (file.exists()) {
                ZipFile(file).use { zip ->
                    zip.charset = currentCharset
                    val entriesList = zip.fileHeaders.asSequence()
                        .filter { !it.isDirectory }
                        .map { it.fileName }
                        .toList()
                    
                    if (filePath.endsWith(".eyp", ignoreCase = true)) {
                        zipEntries = entriesList.sortedWith(compareByDescending { entryName ->
                            SUPPORTED_EXTENSIONS.any { entryName.endsWith(it, ignoreCase = true) }
                        })
                    } else {
                        zipEntries = entriesList
                    }
                }
                errorMessage = null
            } else {
                errorMessage = context.getString(R.string.error_file_not_found)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = context.getString(R.string.error_file_read_failed, e.localizedMessage)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(filePath, currentCharset) {
        withContext(Dispatchers.IO) {
            loadZipEntries()
        }
    }

    fun openZipEntry(name: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                ZipFile(file).use { zip ->
                    zip.charset = currentCharset
                    val header = zip.getFileHeader(name)
                    if (header != null && !header.isDirectory) {
                        val entryName = name.substringAfterLast('/')
                        val tempDir = File(context.cacheDir, "zip_temp_${System.currentTimeMillis()}").apply { mkdirs() }
                        val tempFile = File(tempDir, entryName)
                        
                        zip.getInputStream(header).use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onEntryClick(tempFile.absolutePath, entryName)
                        }
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
                        title = displayName,
                        onRenameClick = onRenameClick
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!filePath.endsWith(".eyp", ignoreCase = true)) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = TooltipAnchorPosition.Below
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(id = R.string.extract_zip))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = {
                                Toast.makeText(context, context.getString(R.string.select_extraction_location), Toast.LENGTH_LONG).show()
                                folderPickerLauncher.launch(null)
                            }) {
                                Icon(Icons.Default.Unarchive, contentDescription = stringResource(id = R.string.extract_zip))
                            }
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Below
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.save))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { saveLauncher.launch(displayName) }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Below
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    withContext(Dispatchers.IO) {
                        loadZipEntries()
                    }
                    delay(500)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (isLoading) {
                    WaitScreenOverlay(
                        show = true,
                        message = stringResource(id = R.string.loading)
                    )
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(id = R.string.file_count, zipEntries.size),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )

                        HorizontalDivider()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(zipEntries, key = { it }) { name ->
                                val isSupported = SUPPORTED_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value != SwipeToDismissBoxValue.Settled) {
                                            selectedEntry = name
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
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .alpha(if (isSupported) 1f else 0.5f)
                                                .combinedClickable(
                                                    onClick = { openZipEntry(name) },
                                                    onLongClick = {
                                                        selectedEntry = name
                                                        showSheet = true
                                                    }
                                                ),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                WaitScreenOverlay(
                    show = isExtracting,
                    message = stringResource(id = R.string.extracting)
                )
            }
        }
    }

    if (showSheet && selectedEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = selectedEntry!!.substringAfterLast('/'),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                OptionItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = stringResource(id = R.string.open_action),
                    onClick = {
                        showSheet = false
                        openZipEntry(selectedEntry!!)
                    }
                )
            }
        }
    }
}
