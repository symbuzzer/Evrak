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
import androidx.compose.material.icons.filled.Fullscreen
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
    onRenameClick: (String) -> Unit = {}
) {
    var textContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val isDocumentSearchEnabled = remember(context) { isDocumentSearchEnabled(context) }
    var isFullScreen by remember { mutableStateOf(false) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }
    var hasSearched by remember { mutableStateOf(false) }
    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }

    val isEvrakLog = filePath.endsWith(".evraklog", ignoreCase = true)

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
                        if (isDocumentSearchEnabled) {
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
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
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
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(stringResource(id = R.string.share))
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = {
                                if (isEvrakLog) {
                                    shareEvrakLogEmail(context, filePath, displayName)
                                } else {
                                    onShareClick()
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
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
            if (isDocumentSearchEnabled && isSearchOpen && !isFullScreen) {
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
