package com.avalibeyaz.evrak.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.avalibeyaz.evrak.MainViewModel
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ExperimentalFeaturesDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCapturingLogcat by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp),
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.experimental_features),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.experimental_features_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                ExperimentalButtonItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(id = R.string.logcat_feature_title),
                    isLoading = isCapturingLogcat,
                    onClick = {
                        if (!isCapturingLogcat) {
                            isCapturingLogcat = true
                            coroutineScope.launch {
                                exportLogcatAndOpen(context, viewModel, onDismiss)
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ExperimentalButtonItem(
    icon: ImageVector,
    title: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

private suspend fun exportLogcatAndOpen(
    context: Context,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val cutoffMs = nowMs - 60_000L

        val pid = android.os.Process.myPid().toString()
        val lines = ArrayList<String>()

        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid", pid))
            val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val cal = Calendar.getInstance()
            val currentYear = cal[Calendar.YEAR]
            var inWindow = false

            process.inputStream.bufferedReader().useLines { seq ->
                seq.forEach { line ->
                    if (line.length >= 18) {
                        val datePart = line.substring(0, 18)
                        try {
                            val parsedDate = sdf.parse(datePart)
                            if (parsedDate != null) {
                                cal.time = parsedDate
                                cal[Calendar.YEAR] = currentYear
                                val lineMs = cal.timeInMillis
                                inWindow = (lineMs >= cutoffMs)
                            }
                        } catch (_: Exception) {}
                    }
                    if (inWindow) {
                        lines.add(line)
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback if filtering resulted in empty list
        if (lines.isEmpty()) {
            try {
                val fallbackProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid", pid))
                fallbackProcess.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { lines.add(it) }
                }
                fallbackProcess.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val content = if (lines.isNotEmpty()) lines.joinToString("\n") else "Logcat kaydı bulunamadı."

        val dateFormat = SimpleDateFormat("yy-MM-dd-HH-mm-ss", Locale.US)
        val fileName = "logcat-${dateFormat.format(Date(nowMs))}.evraklog"

        val cacheDir = File(context.filesDir, "evrak_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val file = File(cacheDir, "${System.currentTimeMillis()}_$fileName")
        file.writeText(content)

        withContext(Dispatchers.Main) {
            viewModel.ensureEvrak(file.absolutePath, fileName) { evrak ->
                openDocumentTask(context, evrak.path, evrak.name)
                onDismiss()
            }
        }
    }
}
