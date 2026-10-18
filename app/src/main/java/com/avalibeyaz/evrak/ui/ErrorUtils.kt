package com.avalibeyaz.evrak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avalibeyaz.evrak.MainViewModel
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.launch

@Composable
fun ErrorDialog(
    errorMessage: String,
    viewModel: MainViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    val errorTimeMs = remember { System.currentTimeMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.error)) },
        text = {
            Column {
                Text(text = errorMessage)
                if (isCapturing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(id = R.string.preparing),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                        coroutineScope.launch {
                            exportLogcatAndOpen(
                                context = context,
                                viewModel = viewModel,
                                onDismiss = {
                                    isCapturing = false
                                    onDismiss()
                                },
                                errorTimeMs = errorTimeMs
                            )
                        }
                    }
                },
                enabled = !isCapturing
            ) {
                Text(text = stringResource(id = R.string.send_feedback_to_developer))
            }
        }
    )
}

@Composable
fun ErrorView(
    errorMessage: String,
    viewModel: MainViewModel = viewModel(),
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    val errorTimeMs = remember { System.currentTimeMillis() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            coroutineScope.launch {
                                exportLogcatAndOpen(
                                    context = context,
                                    viewModel = viewModel,
                                    onDismiss = {
                                        isCapturing = false
                                    },
                                    errorTimeMs = errorTimeMs
                                )
                            }
                        }
                    },
                    enabled = !isCapturing
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(id = R.string.send_feedback_to_developer))
                }
                if (onBackClick != null) {
                    Button(onClick = onBackClick) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }
}
