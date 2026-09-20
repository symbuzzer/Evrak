package com.avalibeyaz.evrak.ui

import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String,
    displayName: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRenameClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val file = File(filePath)
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
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (isFullScreen) {
                val w = context.findActivity()?.window
                if (w != null) {
                    val controller = WindowCompat.getInsetsController(w, w.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    var loadError by remember { mutableStateOf<String?>(null) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffset by animateOffsetAsState(targetValue = offset, label = "offset")

    val isHeic = remember(filePath) {
        filePath.endsWith(".heic", true)
    }
    var heicBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(filePath) {
        if (isHeic) {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    try {
                        val source = android.graphics.ImageDecoder.createSource(file)
                        heicBitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                            val maxSide = 2500
                            if (info.size.width > maxSide || info.size.height > maxSide) {
                                val ratio = Math.max(info.size.width.toFloat() / maxSide, info.size.height.toFloat() / maxSide)
                                val targetWidth = (info.size.width / ratio).toInt()
                                val targetHeight = (info.size.height / ratio).toInt()
                                decoder.setTargetSize(targetWidth, targetHeight)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("Evrak", "ImageDecoder failed for HEIC, trying BitmapFactory: ${e.message}")
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                        
                        val maxSide = 2500
                        var sampleSize = 1
                        if (options.outWidth > maxSide || options.outHeight > maxSide) {
                            val largest = Math.max(options.outWidth, options.outHeight)
                            sampleSize = Math.ceil(largest.toDouble() / maxSide).toInt()
                        }
                        
                        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                        if (bitmap != null) {
                            heicBitmap = bitmap
                        } else {
                            loadError = context.getString(R.string.error_heic_codec_missing)
                        }
                    }
                }
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                loadError = context.getString(R.string.error_heic_open_failed, e.localizedMessage ?: "Unknown")
                isLoading = false
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            when {
                filePath.endsWith(".png", true) -> "image/png"
                filePath.endsWith(".gif", true) -> "image/gif"
                filePath.endsWith(".webp", true) -> "image/webp"
                filePath.endsWith(".bmp", true) -> "image/bmp"
                filePath.endsWith(".heic", true) -> "image/heic"
                filePath.endsWith(".avif", true) -> "image/avif"
                else -> "image/jpeg"
            }
        )
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    file.inputStream().use { input ->
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

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { MarqueeTitle(title = displayName, onRenameClick = onRenameClick) },
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
                            IconButton(onClick = { saveLauncher.launch(displayName) }) {
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
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val maxWidth = constraints.maxWidth.toFloat()
            val maxHeight = constraints.maxHeight.toFloat()

            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                val extraWidth = (maxWidth * newScale - maxWidth).coerceAtLeast(0f)
                val extraHeight = (maxHeight * newScale - maxHeight).coerceAtLeast(0f)

                val maxX = extraWidth / 2
                val maxY = extraHeight / 2

                scale = newScale
                offset = Offset(
                    x = (offset.x + offsetChange.x).coerceIn(-maxX, maxX),
                    y = (offset.y + offsetChange.y).coerceIn(-maxY, maxY)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 3f
                                    offset = Offset.Zero
                                }
                            }
                        )
                    }
                    .transformable(state = state),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(if (isHeic) heicBitmap else file)
                        .apply {
                            if (!isHeic && (filePath.endsWith(".gif", true) || filePath.endsWith(".webp", true))) {
                                decoderFactory(ImageDecoderDecoder.Factory())
                            }
                        }
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onLoading = { if (!isHeic) isLoading = true },
                    onSuccess = { if (!isHeic) isLoading = false },
                    onError = { state ->
                        if (!isHeic) {
                            isLoading = false
                            loadError = state.result.throwable.message ?: "Unknown Coil error"
                            android.util.Log.e("Evrak", "Coil error loading $filePath: ${state.result.throwable}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = animatedScale,
                            scaleY = animatedScale,
                            translationX = animatedOffset.x,
                            translationY = animatedOffset.y
                        )
                )
            }
        }

        WaitScreenOverlay(
            show = isLoading,
            message = stringResource(id = R.string.loading)
        )

        loadError?.let { error ->
            AlertDialog(
                onDismissRequest = { loadError = null },
                title = { Text(text = stringResource(id = R.string.error)) },
                text = { Text(text = error) },
                confirmButton = {
                    TextButton(onClick = { loadError = null }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            )
        }
    }
}
