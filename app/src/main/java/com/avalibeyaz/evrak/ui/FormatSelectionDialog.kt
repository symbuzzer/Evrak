package com.avalibeyaz.evrak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R

@Composable
fun FormatSelectionDialog(
    extension: String,
    onDismiss: () -> Unit,
    onFormatSelected: (usePdf: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.select_format)) },
        text = {
            Column {
                FormatOptionItem(
                    icon = Icons.Default.Description,
                    label = stringResource(id = R.string.format_original, extension.uppercase()),
                    onClick = {
                        onFormatSelected(false)
                        onDismiss()
                    }
                )
                FormatOptionItem(
                    icon = Icons.Default.PictureAsPdf,
                    label = stringResource(id = R.string.format_pdf, "PDF"),
                    onClick = {
                        onFormatSelected(true)
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun FormatOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EvrakLogShareDialog(
    onDismiss: () -> Unit,
    onSendToDeveloper: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.share_evraklog_title)) },
        text = {
            Column {
                FormatOptionItem(
                    icon = Icons.Default.Email,
                    label = stringResource(id = R.string.send_to_developer),
                    onClick = {
                        onSendToDeveloper()
                        onDismiss()
                    }
                )
                FormatOptionItem(
                    icon = Icons.Default.Share,
                    label = stringResource(id = R.string.share),
                    onClick = {
                        onShare()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}
