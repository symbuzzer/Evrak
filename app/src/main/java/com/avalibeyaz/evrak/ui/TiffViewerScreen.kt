package com.avalibeyaz.evrak.ui

import android.graphics.Bitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import java.io.File
import android.util.LruCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiffViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRenameClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }

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
    var pageCount by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    
    var isConverting by remember { mutableStateOf(false) }
    var conversionMessage by remember { mutableStateOf("") }
    val defaultConvertingMessage = stringResource(id = R.string.converting)
    var showFormatDialog by remember { mutableStateOf<String?>(null) }

    var renderer by remember { mutableStateOf<TiffRenderer?>(null) }
    val mutex = remember { Mutex() }
    val bitmapCache = remember { LruCache<Int, Bitmap>(10) }

    DisposableEffect(Unit) {
        onDispose {
            bitmapCache.evictAll()
        }
    }

    LaunchedEffect(filePath) {
        isLoading = true
        bitmapCache.evictAll()
        renderer = null
        pageCount = 0

        val file = File(filePath)
        if (!file.exists()) {
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var tiffRenderer: TiffRenderer? = null

            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                tiffRenderer = TiffRenderer(pfd)
                renderer = tiffRenderer
                pageCount = tiffRenderer.pageCount()
                withContext(Dispatchers.Main) { isLoading = false }
                
                awaitCancellation()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                loadError = FileErrorUtils.getStandardizedErrorMessage(context, e)
                withContext(Dispatchers.Main) { isLoading = false }
            } finally {
                withContext(NonCancellable) {
                    renderer = null
                    mutex.withLock {
                        try { tiffRenderer?.close() } catch (_: Exception) {}
                        try { pfd?.close() } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val saveTiffLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/tiff")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        File(filePath).inputStream().use { input ->
                            input.copyTo(output)
                        }
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

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    conversionMessage = defaultConvertingMessage
                    isConverting = true
                }
                try {
                    val tempPdf = File(context.cacheDir, "temp_convert.pdf")
                    val result = DocumentConverter.convert(File(filePath), tempPdf, context)
                    if (result is DocumentConverter.ConversionResult.Success) {
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            tempPdf.inputStream().use { input -> input.copyTo(output) }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                        }
                    } else if (result is DocumentConverter.ConversionResult.Error) {
                        withContext(Dispatchers.Main) {
                            loadError = context.getString(R.string.error_conversion_failed, result.message)
                            Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isConverting = false
                }
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val state = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        if (loadError != null) {
                            Text(text = stringResource(id = R.string.app_name))
                        } else {
                            MarqueeTitle(
                                title = displayName,
                                onRenameClick = onRenameClick
                            )
                        }
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
                            IconButton(onClick = {
                                showFormatDialog = "save"
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
                            IconButton(onClick = {
                                showFormatDialog = "share"
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loadError != null) {
                ErrorView(
                    errorMessage = loadError!!,
                    onBackClick = onBackClick
                )
            } else {
                val scrollbarAdapter = rememberLazyColumnScrollbarAdapter(listState = listState)
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
                            TiffPageItem(filePath = filePath, renderer = renderer, index = index, mutex = mutex, cache = bitmapCache)
                        }
                    }

                    DraggableScrollbar(
                        adapter = scrollbarAdapter
                    )

                    val currentPage by remember {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val visibleItems = layoutInfo.visibleItemsInfo
                            if (visibleItems.isEmpty()) {
                                1
                            } else if (!listState.canScrollForward) {
                                pageCount
                            } else {
                                val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                                val mostVisible = visibleItems.minByOrNull { item ->
                                    val itemCenter = item.offset + item.size / 2
                                    kotlin.math.abs(itemCenter - viewportCenter)
                                }
                                (mostVisible?.index ?: listState.firstVisibleItemIndex) + 1
                            }
                        }
                    }
                    PageNumberIndicator(
                        currentPage = currentPage,
                        totalPages = pageCount
                    )
                }
            }
            
            WaitScreenOverlay(
                show = isLoading,
                message = stringResource(id = R.string.loading)
            )

            WaitScreenOverlay(
                show = isConverting,
                message = conversionMessage
            )
        }
    }

    if (showFormatDialog != null) {
        FormatSelectionDialog(
            extension = "TIFF",
            onDismiss = { showFormatDialog = null },
            onFormatSelected = { usePdf ->
                if (showFormatDialog == "save") {
                    if (usePdf) {
                        val newName = displayName.substringBeforeLast(".") + ".pdf"
                        savePdfLauncher.launch(newName)
                    } else {
                        saveTiffLauncher.launch(displayName)
                    }
                } else {
                    scope.launch(Dispatchers.IO) {
                        if (usePdf) {
                            withContext(Dispatchers.Main) {
                                conversionMessage = defaultConvertingMessage
                                isConverting = true
                            }
                            try {
                                val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convert(File(filePath), tempPdf, context)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    DocumentConverter.shareFile(context, tempPdf, "application/pdf")
                                }
                            } finally {
                                isConverting = false
                            }
                        } else {
                            onShareClick()
                        }
                    }
                }
            }
        )
    }
}


@Composable
fun TiffPageItem(filePath: String, renderer: TiffRenderer?, index: Int, mutex: Mutex, cache: LruCache<Int, Bitmap>) {
    val context = LocalContext.current
    val cached = cache.get(index)
    var renderError by remember(index) { mutableStateOf<String?>(null) }
    val bitmapState = produceState<Bitmap?>(initialValue = cached, renderer, index) {
        if (renderer == null || value != null) return@produceState
        try {
            value = withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        val page = renderer.openPage(index)
                        try {
                            val maxDimension = 2048f
                            val scale = (maxDimension / maxOf(page.width, page.height)).coerceAtMost(1f)
                            val targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
                            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                            
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val tiffBitmap = TiffBitmap(bitmap)
                            page.render(tiffBitmap, null, null, TiffRenderMode.FOR_DISPLAY)
                            
                            cache.put(index, bitmap)
                            bitmap
                        } finally {
                            try { page.close() } catch (_: Exception) {}
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        e.printStackTrace()
                        val fallbackBitmap = decodeJpegTiffPage(File(filePath), index)
                        if (fallbackBitmap != null) {
                            cache.put(index, fallbackBitmap)
                            fallbackBitmap
                        } else {
                            val msg = e.message ?: "unknown"
                            val formattedMsg = if (msg.contains("compression method", ignoreCase = true) || msg.contains("not configured", ignoreCase = true)) {
                                val method = getTiffCompressionMethod(File(filePath))
                                val compressionInfo = if (method != "Unknown") method else msg
                                context.getString(R.string.tiff_compression_error, compressionInfo)
                            } else {
                                context.getString(R.string.page_load_error, msg.ifEmpty { e.javaClass.simpleName })
                            }
                            renderError = formattedMsg
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackBitmap = decodeJpegTiffPage(File(filePath), index)
            if (fallbackBitmap != null) {
                cache.put(index, fallbackBitmap)
                fallbackBitmap
            } else {
                val msg = e.message ?: "unknown"
                val formattedMsg = if (msg.contains("compression method", ignoreCase = true) || msg.contains("not configured", ignoreCase = true)) {
                    val method = getTiffCompressionMethod(File(filePath))
                    val compressionInfo = if (method != "Unknown") method else msg
                    context.getString(R.string.tiff_compression_error, compressionInfo)
                } else {
                    context.getString(R.string.page_load_error, msg.ifEmpty { e.javaClass.simpleName })
                }
                renderError = formattedMsg
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        val bitmap = bitmapState.value
        val error = renderError
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                contentScale = ContentScale.FillWidth
            )
        } else if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
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

private fun getTiffCompressionMethod(file: File): String {
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(8)
            if (input.read(header) < 8) return@use "Unknown"
            val isLe = header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte()
            val magic = if (isLe) {
                ((header[3].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)
            } else {
                ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            }
            if (magic != 42) return@use "Unknown"

            val ifdOffset = if (isLe) {
                ((header[7].toInt() and 0xFF) shl 24) or
                ((header[6].toInt() and 0xFF) shl 16) or
                ((header[5].toInt() and 0xFF) shl 8) or
                (header[4].toInt() and 0xFF)
            } else {
                ((header[4].toInt() and 0xFF) shl 24) or
                ((header[5].toInt() and 0xFF) shl 16) or
                ((header[6].toInt() and 0xFF) shl 8) or
                (header[7].toInt() and 0xFF)
            }

            val buffer = ByteArray(2)
            input.channel.position(ifdOffset.toLong())
            if (input.read(buffer) < 2) return@use "Unknown"
            val numEntries = if (isLe) {
                ((buffer[1].toInt() and 0xFF) shl 8) or (buffer[0].toInt() and 0xFF)
            } else {
                ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
            }

            val entryBuffer = ByteArray(12)
            var compressionCode = -1
            for (i in 0 until numEntries) {
                if (input.read(entryBuffer) < 12) break
                val tag = if (isLe) {
                    ((entryBuffer[1].toInt() and 0xFF) shl 8) or (entryBuffer[0].toInt() and 0xFF)
                } else {
                    ((entryBuffer[0].toInt() and 0xFF) shl 8) or (entryBuffer[1].toInt() and 0xFF)
                }
                if (tag == 259) {
                    val valOrOffset = if (isLe) {
                        ((entryBuffer[11].toInt() and 0xFF) shl 24) or
                        ((entryBuffer[10].toInt() and 0xFF) shl 16) or
                        ((entryBuffer[9].toInt() and 0xFF) shl 8) or
                        (entryBuffer[8].toInt() and 0xFF)
                    } else {
                        ((entryBuffer[8].toInt() and 0xFF) shl 24) or
                        ((entryBuffer[9].toInt() and 0xFF) shl 16) or
                        ((entryBuffer[10].toInt() and 0xFF) shl 8) or
                        (entryBuffer[11].toInt() and 0xFF)
                    }
                    compressionCode = valOrOffset and 0xFFFF
                    break
                }
            }

            when (compressionCode) {
                1 -> "None (Uncompressed)"
                2 -> "CCITT Group 3 1-D Modified Huffman"
                3 -> "CCITT Group 3 Fax"
                4 -> "CCITT Group 4 Fax"
                5 -> "LZW"
                6 -> "JPEG (Old Style)"
                7 -> "JPEG"
                8 -> "Adobe Deflate / Deflate"
                32773 -> "PackBits"
                32946 -> "Deflate"
                else -> if (compressionCode != -1) "Code $compressionCode" else "Unknown"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "Unknown"
    }
}

private fun decodeJpegTiffPage(file: File, targetIndex: Int): Bitmap? {
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(8)
            if (input.read(header) < 8) return@use null
            val isLe = header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte()
            val magic = if (isLe) {
                ((header[3].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)
            } else {
                ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            }
            if (magic != 42) return@use null

            var ifdOffset = if (isLe) {
                ((header[7].toInt() and 0xFF) shl 24) or
                ((header[6].toInt() and 0xFF) shl 16) or
                ((header[5].toInt() and 0xFF) shl 8) or
                (header[4].toInt() and 0xFF)
            } else {
                ((header[4].toInt() and 0xFF) shl 24) or
                ((header[5].toInt() and 0xFF) shl 16) or
                ((header[6].toInt() and 0xFF) shl 8) or
                (header[7].toInt() and 0xFF)
            }

            var currentIndex = 0
            var stripOffsets = listOf<Long>()
            var stripByteCounts = listOf<Long>()

            while (ifdOffset != 0) {
                input.channel.position(ifdOffset.toLong())
                val countBuf = ByteArray(2)
                if (input.read(countBuf) < 2) break
                val numEntries = if (isLe) {
                    ((countBuf[1].toInt() and 0xFF) shl 8) or (countBuf[0].toInt() and 0xFF)
                } else {
                    ((countBuf[0].toInt() and 0xFF) shl 8) or (countBuf[1].toInt() and 0xFF)
                }

                val entryBuffer = ByteArray(12)
                var localOffsets = listOf<Long>()
                var localByteCounts = listOf<Long>()

                for (i in 0 until numEntries) {
                    if (input.read(entryBuffer) < 12) break
                    val tag = if (isLe) {
                        ((entryBuffer[1].toInt() and 0xFF) shl 8) or (entryBuffer[0].toInt() and 0xFF)
                    } else {
                        ((entryBuffer[0].toInt() and 0xFF) shl 8) or (entryBuffer[1].toInt() and 0xFF)
                    }
                    val type = if (isLe) {
                        ((entryBuffer[3].toInt() and 0xFF) shl 8) or (entryBuffer[2].toInt() and 0xFF)
                    } else {
                        ((entryBuffer[2].toInt() and 0xFF) shl 8) or (entryBuffer[3].toInt() and 0xFF)
                    }
                    val count = if (isLe) {
                        ((entryBuffer[7].toInt() and 0xFF) shl 24) or
                        ((entryBuffer[6].toInt() and 0xFF) shl 16) or
                        ((entryBuffer[5].toInt() and 0xFF) shl 8) or
                        (entryBuffer[4].toInt() and 0xFF)
                    } else {
                        ((entryBuffer[4].toInt() and 0xFF) shl 24) or
                        ((entryBuffer[5].toInt() and 0xFF) shl 16) or
                        ((entryBuffer[6].toInt() and 0xFF) shl 8) or
                        (entryBuffer[7].toInt() and 0xFF)
                    }
                    val valOrOffset = if (isLe) {
                        ((entryBuffer[11].toInt() and 0xFF) shl 24) or
                        ((entryBuffer[10].toInt() and 0xFF) shl 16) or
                        ((entryBuffer[9].toInt() and 0xFF) shl 8) or
                        (entryBuffer[8].toInt() and 0xFF)
                    } else {
                        ((entryBuffer[8].toInt() and 0xFF) shl 24) or
                        ((entryBuffer[9].toInt() and 0xFF) shl 16) or
                        ((entryBuffer[10].toInt() and 0xFF) shl 8) or
                        (entryBuffer[11].toInt() and 0xFF)
                    }

                    when (tag) {
                        273 -> localOffsets = readTiffValues(input, isLe, type, count.toLong(), valOrOffset)
                        279 -> localByteCounts = readTiffValues(input, isLe, type, count.toLong(), valOrOffset)
                    }
                }

                if (currentIndex == targetIndex) {
                    stripOffsets = localOffsets
                    stripByteCounts = localByteCounts
                    break
                }

                val nextIfdBuf = ByteArray(4)
                if (input.read(nextIfdBuf) < 4) break
                ifdOffset = if (isLe) {
                    ((nextIfdBuf[3].toInt() and 0xFF) shl 24) or
                    ((nextIfdBuf[2].toInt() and 0xFF) shl 16) or
                    ((nextIfdBuf[1].toInt() and 0xFF) shl 8) or
                    (nextIfdBuf[0].toInt() and 0xFF)
                } else {
                    ((nextIfdBuf[0].toInt() and 0xFF) shl 24) or
                    ((nextIfdBuf[1].toInt() and 0xFF) shl 16) or
                    ((nextIfdBuf[2].toInt() and 0xFF) shl 8) or
                    (nextIfdBuf[3].toInt() and 0xFF)
                }
                currentIndex++
            }

            if (stripOffsets.isNotEmpty() && stripByteCounts.isNotEmpty()) {
                val offset = stripOffsets[0]
                val length = stripByteCounts[0].toInt()
                val jpegBytes = ByteArray(length)
                val fis = input as? java.io.FileInputStream
                fis?.channel?.position(offset)
                input.read(jpegBytes)
                return@use android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, length)
            }
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun readTiffValues(input: java.io.InputStream, isLe: Boolean, type: Int, count: Long, valOrOffset: Int): List<Long> {
    val result = mutableListOf<Long>()
    val fis = input as? java.io.FileInputStream
    if (count == 1L) {
        result.add(valOrOffset.toLong() and 0xFFFFFFFFL)
    } else {
        val currentPos = fis?.channel?.position() ?: 0L
        fis?.channel?.position(valOrOffset.toLong() and 0xFFFFFFFFL)
        val byteSize = when (type) {
            3 -> 2
            4 -> 4
            else -> 4
        }
        val buf = ByteArray((count * byteSize).toInt())
        input.read(buf)
        for (i in 0 until count.toInt()) {
            val v = if (byteSize == 2) {
                val idx = i * 2
                if (isLe) ((buf[idx+1].toInt() and 0xFF) shl 8) or (buf[idx].toInt() and 0xFF)
                else ((buf[idx].toInt() and 0xFF) shl 8) or (buf[idx+1].toInt() and 0xFF)
            } else {
                val idx = i * 4
                if (isLe) {
                    ((buf[idx+3].toInt() and 0xFF) shl 24) or ((buf[idx+2].toInt() and 0xFF) shl 16) or ((buf[idx+1].toInt() and 0xFF) shl 8) or (buf[idx].toInt() and 0xFF)
                } else {
                    ((buf[idx].toInt() and 0xFF) shl 24) or ((buf[idx+1].toInt() and 0xFF) shl 16) or ((buf[idx+2].toInt() and 0xFF) shl 8) or (buf[idx+3].toInt() and 0xFF)
                }
            }
            result.add(v.toLong() and 0xFFFFFFFFL)
        }
        fis?.channel?.position(currentPos)
    }
    return result
}
