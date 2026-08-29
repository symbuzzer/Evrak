package com.avalibeyaz.evrak.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.avalibeyaz.evrak.BuildConfig
import com.avalibeyaz.evrak.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${stringResource(id = R.string.about_title)} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.about_developed_by),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Supported formats group (centered)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.about_supported_formats),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.about_supported_formats_list),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                AboutLinkItem(
                    icon = Icons.Default.IntegrationInstructions,
                    description = stringResource(id = R.string.about_celse_integration_desc),
                    onClick = { uriHandler.openUri("https://github.com/symbuzzer/UDE_stub") }
                )

                AboutLinkItem(
                    icon = Icons.Default.Shop,
                    description = stringResource(id = R.string.about_view_on_play_store_desc),
                    onClick = { uriHandler.openUri("https://play.google.com/store/apps/details?id=com.avalibeyaz.evrak") }
                )

                AboutLinkItem(
                    icon = Icons.AutoMirrored.Filled.Article,
                    description = stringResource(id = R.string.about_view_libraries_desc),
                    onClick = { uriHandler.openUri("https://github.com/symbuzzer/Evrak#kullan%C4%B1lan-k%C3%BCt%C3%BCphaneler-ve-lisanslar%C4%B1") }
                )

                AboutLinkItem(
                    icon = Icons.Default.Code,
                    description = stringResource(id = R.string.about_source_code_desc),
                    onClick = { uriHandler.openUri("https://github.com/symbuzzer/Evrak") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.close),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                )
            }
        }
    )
}

@Composable
private fun AboutLinkItem(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
