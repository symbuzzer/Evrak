package com.avalibeyaz.evrak.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiffViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // TiffRenderer management
    var renderer by remember { mutableStateOf<TiffRenderer?>(null) }
    val mutex = remember { Mutex() }

    DisposableEffect(filePath) {
        val file = File(filePath)
        if (!file.exists()) return@DisposableEffect onDispose {}
        
        var pfd: ParcelFileDescriptor? = null
        var tiffRenderer: TiffRenderer? = null
        
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            tiffRenderer = TiffRenderer(pfd)
            renderer = tiffRenderer
            pageCount = tiffRenderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            loadError = "TIFF dosyası açılamadı: ${e.message}"
            tiffRenderer?.close()
            pfd?.close()
        }

        onDispose {
            renderer?.close()
            pfd?.close()
        }
    }

    // Save launcher
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/tiff")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    File(filePath).inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val state = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
    }

    // Track current page based on list scroll position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (loadError != null) {
                        Text(text = stringResource(id = R.string.app_name))
                    } else {
                        MarqueeTitle(
                            title = displayName,
                            prefix = stringResource(id = R.string.page_format, currentPage, pageCount)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        saveLauncher.launch(displayName)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val viewHeight = maxHeight
            
            if (loadError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(text = loadError!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick) {
                            Text(text = stringResource(id = R.string.ok))
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // List Container
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = if (scale > 1.1f) 1f else 3f
                                    }
                                )
                            }
                            .transformable(state = state)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = animatedScale,
                                    scaleY = animatedScale
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = if (pageCount == 1) Arrangement.Center else Arrangement.spacedBy(16.dp)
                        ) {
                            items(pageCount) { index ->
                                TiffPageItem(renderer = renderer, index = index, mutex = mutex)
                            }
                        }
                    }

                    // SUPER ROBUST DRAGGABLE SCROLLBAR
                    if (pageCount > 1) {
                        val thumbHeight = (viewHeight.value / pageCount).coerceAtLeast(60f).dp
                        val scrollableTrackHeight = viewHeight - thumbHeight
                        
                        // Track list position to update thumb
                        val listProgress by remember {
                            derivedStateOf {
                                if (pageCount > 1) {
                                    val firstVisible = listState.firstVisibleItemIndex
                                    val total = pageCount - 1
                                    (firstVisible.toFloat() / total).coerceIn(0f, 1f)
                                } else 0f
                            }
                        }
                        
                        // Drag state
                        var isDragging by remember { mutableStateOf(false) }
                        var dragOffset by remember { mutableFloatStateOf(0f) }
                        
                        // The actual visible thumb offset depends on whether we are dragging or not
                        val thumbOffset = if (isDragging) {
                            dragOffset.dp
                        } else {
                            scrollableTrackHeight * listProgress
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(60.dp) // Massive hit area for reliable touch
                                .pointerInput(pageCount, viewHeight) {
                                    detectVerticalDragGestures(
                                        onDragStart = { offset ->
                                            isDragging = true
                                            // Initialize dragOffset based on current list position
                                            dragOffset = (listProgress * scrollableTrackHeight.toPx()).toDp().value
                                        },
                                        onDragEnd = { isDragging = false },
                                        onDragCancel = { isDragging = false },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            val totalPx = scrollableTrackHeight.toPx()
                                            val currentOffsetPx = dragOffset.dp.toPx()
                                            val newOffsetPx = (currentOffsetPx + dragAmount).coerceIn(0f, totalPx)
                                            dragOffset = newOffsetPx.toDp().value
                                            
                                            // Scroll the list
                                            val newProgress = if (totalPx > 0) newOffsetPx / totalPx else 0f
                                            val targetIndex = (newProgress * (pageCount - 1)).toInt()
                                            
                                            scope.launch {
                                                listState.scrollToItem(targetIndex)
                                            }
                                        }
                                    )
                                }
                        ) {
                            // Track Visual (Optional but helpful)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .background(Color.Gray.copy(alpha = 0.05f))
                            )

                            // Visible Thumb
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = thumbHeight)
                                    .offset(y = thumbOffset)
                                    .align(Alignment.TopEnd)
                                    .padding(end = 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDragging) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TiffPageItem(renderer: TiffRenderer?, index: Int, mutex: Mutex) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, renderer, index) {
        if (renderer == null) return@produceState
        value = withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val page = renderer.openPage(index)
                    // Create bitmap for Android
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    // Wrap it for TiffRenderer
                    val tiffBitmap = TiffBitmap(bitmap)
                    // Render
                    page.render(tiffBitmap, null, null, TiffRenderMode.FOR_DISPLAY)
                    page.close()
                    bitmap
                } catch (e: Throwable) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
