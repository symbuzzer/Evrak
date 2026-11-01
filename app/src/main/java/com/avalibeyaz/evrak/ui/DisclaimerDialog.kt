package com.avalibeyaz.evrak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.avalibeyaz.evrak.R
import com.avalibeyaz.evrak.data.DisclaimerManager

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var hasClickedLink by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(5) }

    LaunchedEffect(hasClickedLink) {
        if (hasClickedLink) {
            for (i in 5 downTo 1) {
                secondsRemaining = i
                kotlinx.coroutines.delay(1000)
            }
            secondsRemaining = 0
        }
    }

    BackHandler {
        onDecline()
    }

    AlertDialog(
        onDismissRequest = {
            onDecline()
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.Gavel,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.disclaimer_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!hasClickedLink) {
                                hasClickedLink = true
                            }
                            uriHandler.openUri("https://github.com/symbuzzer/Evrak#sorumluluk-reddi")
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.disclaimer_dialog_message),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isEnabled = hasClickedLink && secondsRemaining == 0
            val buttonText = when {
                !hasClickedLink -> stringResource(id = R.string.disclaimer_accept)
                secondsRemaining > 0 -> "${stringResource(id = R.string.disclaimer_accept)} (${secondsRemaining}s)"
                else -> stringResource(id = R.string.disclaimer_accept)
            }

            Button(
                enabled = isEnabled,
                onClick = {
                    DisclaimerManager.setDisclaimerAccepted(context)
                    onAccept()
                }
            ) {
                Text(text = buttonText)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline
            ) {
                Text(text = stringResource(id = R.string.disclaimer_decline))
            }
        }
    )
}
