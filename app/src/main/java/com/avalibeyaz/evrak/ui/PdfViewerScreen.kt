package com.avalibeyaz.evrak.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
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
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: (() -> Unit)? = null,
    saveFilePath: String = filePath,
    saveDisplayName: String = displayName,
    saveMimeType: String = "application/pdf"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    val listState = rememberLazyListState()

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    val mutex = remember { Mutex() }

    DisposableEffect(filePath) {
        val file = File(filePath)
        if (!file.exists()) return@DisposableEffect onDispose {}
        
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fd)
        renderer = pdfRenderer
        pageCount = pdfRenderer.pageCount

        onDispose {
            pdfRenderer.close()
            fd.close()
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(saveMimeType)
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    File(saveFilePath).inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    MarqueeTitle(
                        title = displayName,
                        prefix = stringResource(id = R.string.page_format, currentPage, pageCount)
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
                                Text(stringResource(id = R.string.save))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            if (onSaveClick != null) {
                                onSaveClick()
                            } else {
                                saveLauncher.launch(saveDisplayName)
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
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
                        IconButton(onClick = onShareClick) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                        }
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

            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
            val animatedOffset by animateOffsetAsState(targetValue = offset, label = "offset")

            val state = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 5f)

                val newOffset = if (newScale > 1f) {
                    val rawOffset = offset + panChange * scale

                    val maxX = (newScale - 1f) * (constraints.maxWidth / 2f)
                    val maxY = (newScale - 1f) * (constraints.maxHeight / 2f)

                    Offset(
                        x = rawOffset.x.coerceIn(-maxX, maxX),
                        y = rawOffset.y.coerceIn(-maxY, maxY)
                    )
                } else {
                    Offset.Zero
                }

                scale = newScale
                offset = newOffset
            }

            LaunchedEffect(scale) {
                if (scale <= 1f) {
                    offset = Offset.Zero
                }
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 3f
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        offset = Offset(
                                            x = (centerX - tapOffset.x) * 2f,
                                            y = (centerY - tapOffset.y) * 2f
                                        )
                                    }
                                }
                            )
                        }
                        .transformable(state = state)
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = animatedScale,
                            scaleY = animatedScale,
                            translationX = animatedOffset.x,
                            translationY = animatedOffset.y
                        )

                    LazyColumn(
                        state = listState,
                        modifier = contentModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = if (pageCount == 1) Arrangement.Center else Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPageItem(renderer = renderer, index = index, mutex = mutex)
                        }
                    }
                }

                if (pageCount > 1) {
                    val thumbHeight = (viewHeight.value / pageCount).coerceAtLeast(60f).dp
                    val scrollableTrackHeight = viewHeight - thumbHeight
                    
                    val listProgress by remember {
                        derivedStateOf {
                            if (pageCount > 1) {
                                val firstVisible = listState.firstVisibleItemIndex
                                val total = pageCount - 1
                                (firstVisible.toFloat() / total).coerceIn(0f, 1f)
                            } else 0f
                        }
                    }
                    
                    var isDragging by remember { mutableStateOf(false) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    
                    val thumbOffset = if (isDragging) {
                        dragOffset.dp
                    } else {
                        scrollableTrackHeight * listProgress
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(60.dp)
                            .pointerInput(pageCount, viewHeight) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
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
                                        
                                        val newProgress = if (totalPx > 0) newOffsetPx / totalPx else 0f
                                        val targetIndex = (newProgress * (pageCount - 1)).toInt()
                                        
                                        scope.launch {
                                            listState.scrollToItem(targetIndex)
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .background(Color.Gray.copy(alpha = 0.05f))
                        )

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

@Composable
fun PdfPageItem(renderer: PdfRenderer?, index: Int, mutex: Mutex) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, renderer, index) {
        if (renderer == null) return@produceState
        value = withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val page = renderer.openPage(index)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * 1.5).toInt(),
                        (page.height * 1.5).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                } catch (e: Exception) {
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
