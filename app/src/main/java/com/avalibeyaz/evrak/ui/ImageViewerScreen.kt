package com.avalibeyaz.evrak.ui

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
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val file = File(filePath)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    var loadError by remember { mutableStateOf<String?>(null) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffset by animateOffsetAsState(targetValue = offset, label = "offset")

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            when {
                filePath.endsWith(".png", true) -> "image/png"
                filePath.endsWith(".gif", true) -> "image/gif"
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { MarqueeTitle(title = displayName) },
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
                        .data(file)
                        .decoderFactory(ImageDecoderDecoder.Factory())
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
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
