package com.avalibeyaz.evrak.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // Save launcher
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            selectedEvrak?.let { evrak ->
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
                                    text = "Tümünü sil",
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
                        showSheet = false
                        val file = File(selectedEvrak!!.path)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            val path = selectedEvrak!!.path
                            type = when {
                                path.endsWith(".pdf", true) -> "application/pdf"
                                path.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                path.endsWith(".doc", true) -> "application/msword"
                                path.endsWith(".png", true) -> "image/png"
                                path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
                                path.endsWith(".gif", true) -> "image/gif"
                                else -> "application/octet-stream"
                            }
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
                    }
                )
                
                OptionItem(
                    icon = Icons.Default.Save,
                    label = stringResource(id = R.string.save),
                    onClick = {
                        showSheet = false
                        saveLauncher.launch(selectedEvrak!!.name)
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
            title = { Text(text = "Tümünü sil") },
            text = { Text(text = "Bütün geçmişi ve önbelleğe alınmış dosyaları silmek istediğinize emin misiniz?") },
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
                    .format(Date(evrak.dateOpened))
                    .replace("AM", "ÖÖ")
                    .replace("PM", "ÖS"),
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
