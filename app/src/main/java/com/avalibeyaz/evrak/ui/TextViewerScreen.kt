package com.avalibeyaz.evrak.ui

import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRenameClick: (String) -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    var textContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var isFullScreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }
    var hasSearched by remember { mutableStateOf(false) }
    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }

    val isEvrakLog = filePath.endsWith(".evraklog", ignoreCase = true)
    var showEvrakLogShareDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    DisposableEffect(isFullScreen) {
        val window = context.findActivity()?.window
        if (window != null) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            val w = context.findActivity()?.window
            if (w != null) {
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
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

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists() && file.length() > 0L) {
                    textContent = file.readText(Charset.forName("UTF-8"))
                } else {
                    errorMessage = FileErrorUtils.getStandardizedErrorMessage(context, "not_found")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                errorMessage = FileErrorUtils.getStandardizedErrorMessage(context, e)
            } finally {
                isLoading = false
            }
        }
    }

    fun performTextSearch(query: String) {
        if (query.isBlank() || textContent.isEmpty()) {
            searchMatches = emptyList()
            currentMatchIndex = -1
            hasSearched = true
            return
        }
        val matches = mutableListOf<Int>()
        var idx = textContent.indexOf(query, ignoreCase = true)
        while (idx != -1) {
            matches.add(idx)
            idx = textContent.indexOf(query, idx + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        searchMatches = matches
        currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
        hasSearched = true
    }

    val annotatedText = remember(textContent, searchQuery, searchMatches, currentMatchIndex, hasSearched) {
        if (!hasSearched || searchQuery.isBlank() || searchMatches.isEmpty()) {
            AnnotatedString(textContent)
        } else {
            buildAnnotatedString {
                append(textContent)
                searchMatches.forEachIndexed { idx, matchStart ->
                    val matchEnd = (matchStart + searchQuery.length).coerceAtMost(textContent.length)
                    val isCurrent = idx == currentMatchIndex
                    val backgroundColor = if (isCurrent) Color(0xFFFF9800) else Color(0xFFFFFF00)
                    val textColor = Color.Black
                    addStyle(
                        style = SpanStyle(background = backgroundColor, color = textColor),
                        start = matchStart,
                        end = matchEnd
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
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
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(id = R.string.search_in_document))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = {
                                isSearchOpen = !isSearchOpen
                                if (!isSearchOpen) {
                                    searchQuery = ""
                                    searchMatches = emptyList()
                                    currentMatchIndex = -1
                                    hasSearched = false
                                }
                            }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(id = R.string.search_in_document),
                                    tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                        if (!isEvrakLog) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(id = R.string.full_screen))
                                    }
                                },
                                state = rememberTooltipState()
                            ) {
                                IconButton(onClick = { isFullScreen = true }) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = stringResource(id = R.string.full_screen))
                                }
                            }
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(id = R.string.more_options))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.more_options))
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.open_with)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            val handled = openFileWithSystemChooser(context, filePath)
                                            if (!handled) {
                                                Toast.makeText(context, context.getString(R.string.no_other_apps), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.share)) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            if (isEvrakLog) {
                                                showEvrakLogShareDialog = true
                                            } else {
                                                onShareClick()
                                            }
                                        }
                                    )
                                    if (!isEvrakLog) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(id = R.string.save)) },
                                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                saveLauncher.launch(displayName)
                                            }
                                        )
                                    }
                                    if (!isEvrakLog) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(id = R.string.print)) },
                                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                printFile(context, filePath, displayName) { converting ->
                                                    isPrinting = converting
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.delete), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(text = stringResource(id = R.string.delete)) },
                text = { Text(text = stringResource(id = R.string.confirm_delete)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            if (onDeleteClick != null) {
                                onDeleteClick()
                            } else {
                                try { File(filePath).delete() } catch (_: Exception) {}
                                onBackClick()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(text = stringResource(id = R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                }
            )
        }
        if (showEvrakLogShareDialog) {
            EvrakLogShareDialog(
                onDismiss = { showEvrakLogShareDialog = false },
                onSendToDeveloper = {
                    shareEvrakLogEmail(context, filePath, displayName)
                },
                onShare = {
                    onShareClick()
                }
            )
        }
        val scrollState = rememberScrollState()

        LaunchedEffect(currentMatchIndex, textLayoutResultState) {
            val layoutResult = textLayoutResultState
            if (currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size && layoutResult != null) {
                val matchStart = searchMatches[currentMatchIndex]
                val line = layoutResult.getLineForOffset(matchStart)
                val lineTop = layoutResult.getLineTop(line)
                scrollState.animateScrollTo(lineTop.toInt())
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearchOpen && !isFullScreen) {
                DocumentSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        performTextSearch(searchQuery)
                    },
                    onNext = {
                        if (searchMatches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                        }
                    },
                    onPrevious = {
                        if (searchMatches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                        }
                    },
                    onClose = {
                        isSearchOpen = false
                        searchQuery = ""
                        searchMatches = emptyList()
                        currentMatchIndex = -1
                        hasSearched = false
                    },
                    currentMatch = if (currentMatchIndex >= 0) currentMatchIndex + 1 else 0,
                    totalMatches = searchMatches.size,
                    hasSearched = hasSearched
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color.White)
            ) {
                WaitScreenOverlay(
                    show = isPrinting,
                    message = stringResource(id = R.string.preparing)
                )

                if (isLoading) {
                    WaitScreenOverlay(
                        show = true,
                        message = stringResource(id = R.string.loading)
                    )
                } else if (errorMessage != null) {
                    ErrorView(
                        errorMessage = errorMessage!!,
                        onBackClick = onBackClick
                    )
                } else {
                    val scrollbarAdapter = rememberScrollStateAdapter(scrollState = scrollState)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                                onTextLayout = { layoutResult ->
                                    textLayoutResultState = layoutResult
                                }
                            )
                        }
                    }

                    DraggableScrollbar(
                        adapter = scrollbarAdapter
                    )
                }
            }
        }
    }
}
