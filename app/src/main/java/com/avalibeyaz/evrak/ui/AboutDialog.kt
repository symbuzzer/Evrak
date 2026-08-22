package com.avalibeyaz.evrak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avalibeyaz.evrak.BuildConfig
import com.avalibeyaz.evrak.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(id = R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Developer link (Ali BEYAZ only)
                val developerText = buildAnnotatedString {
                    pushStringAnnotation(tag = "URL", annotation = "https://github.com/symbuzzer")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold
                    )) {
                        append("Ali BEYAZ")
                    }
                    pop()
                    append(" tarafından geliştirilmiştir.")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = developerText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    onClick = { offset ->
                        developerText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
                
                // Description
                Text(
                    text = stringResource(id = R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                // Contact link (bildirin only)
                val contactText = buildAnnotatedString {
                    append("Görüş ve önerilerinizi ")
                    pushStringAnnotation(tag = "URL", annotation = "https://wa.me/905392552070")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold
                    )) {
                        append("bildirin")
                    }
                    pop()
                    append(".")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = contactText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    onClick = { offset ->
                        contactText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            }
        },
        confirmButton = {}
    )
}
