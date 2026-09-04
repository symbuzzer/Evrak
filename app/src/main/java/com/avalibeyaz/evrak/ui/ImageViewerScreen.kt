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
    val scope = rememberCoroutineScope()
    val file = File(filePath)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    var isConverting by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffset by animateOffsetAsState(targetValue = offset, label = "offset")
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

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
                        IconButton(onClick = { showFormatDialog = "save" }) {
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
                        IconButton(onClick = { showFormatDialog = "share" }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.share))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = animatedScale,
                        scaleY = animatedScale,
                        translationX = animatedOffset.x,
                        translationY = animatedOffset.y
                )
            )

            ConversionOverlay(isConverting = isConverting)
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

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destUri ->
            scope.launch(Dispatchers.IO) {
                isConverting = true
                try {
                    val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                    val tempPdf = File(context.cacheDir, pdfName)
                    val result = DocumentConverter.convert(File(filePath), tempPdf, context)
                    if (result is DocumentConverter.ConversionResult.Success) {
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            tempPdf.inputStream().use { input -> input.copyTo(output) }
                        }
                    } else if (result is DocumentConverter.ConversionResult.Error) {
                        withContext(Dispatchers.Main) {
                            loadError = context.getString(R.string.error_conversion_failed, result.message)
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

    if (showFormatDialog != null) {
        val ext = filePath.substringAfterLast(".").uppercase()
        FormatSelectionDialog(
            extension = ext,
            onDismiss = { showFormatDialog = null },
            onFormatSelected = { usePdf ->
                if (showFormatDialog == "save") {
                    if (usePdf) {
                        val newName = displayName.substringBeforeLast(".") + ".pdf"
                        savePdfLauncher.launch(newName)
                    } else {
                        saveLauncher.launch(displayName)
                    }
                } else {
                    if (usePdf) {
                        scope.launch(Dispatchers.IO) {
                            isConverting = true
                            try {
                                val pdfName = displayName.substringBeforeLast(".") + ".pdf"
                                val tempPdf = File(context.cacheDir, pdfName)
                                val result = DocumentConverter.convert(File(filePath), tempPdf, context)
                                if (result is DocumentConverter.ConversionResult.Success) {
                                    DocumentConverter.shareFile(context, tempPdf, "application/pdf")
                                } else if (result is DocumentConverter.ConversionResult.Error) {
                                    withContext(Dispatchers.Main) {
                                        loadError = context.getString(R.string.error_conversion_failed, result.message)
                                    }
                                }
                            } finally {
                                isConverting = false
                            }
                        }
                    } else {
                        onShareClick()
                    }
                }
            }
        )
    }
}
