package com.avalibeyaz.evrak.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
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
        icon = {
            Image(
                painter = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        },
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    append(stringResource(id = R.string.about_developed_by, ""))
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = developerText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { offset ->
                    developerText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }

                // Supported formats group (tightly coupled)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                // Celse integration paragraph
                val celseText = buildAnnotatedString {
                    append(stringResource(id = R.string.about_celse_integration))
                    pushStringAnnotation(tag = "URL", annotation = "https://github.com/symbuzzer/UDE_stub")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(stringResource(id = R.string.about_realize))
                    }
                    pop()
                    append(".")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = celseText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { offset ->
                    celseText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }

                // Play Store link
                val playStoreText = buildAnnotatedString {
                    append(stringResource(id = R.string.about_view_on_play_store))
                    pushStringAnnotation(tag = "URL", annotation = "https://play.google.com/store/apps/details?id=com.avalibeyaz.evrak")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(stringResource(id = R.string.about_view_on_play_store_link))
                    }
                    pop()
                    append(".")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = playStoreText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { offset ->
                    playStoreText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
                
                // Library link
                val libraryText = buildAnnotatedString {
                    append(stringResource(id = R.string.about_view_libraries))
                    pushStringAnnotation(tag = "URL", annotation = "https://github.com/symbuzzer/Evrak#kullan%C4%B1lan-k%C3%BCt%C3%BCphaneler-ve-lisanslar%C4%B1")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(stringResource(id = R.string.about_view))
                    }
                    pop()
                    append(".")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = libraryText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { offset ->
                    libraryText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }

                // Source code link
                val sourceCodeText = buildAnnotatedString {
                    append(stringResource(id = R.string.about_source_code))
                    pushStringAnnotation(tag = "URL", annotation = "https://github.com/symbuzzer/Evrak")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(stringResource(id = R.string.about_view))
                    }
                    pop()
                    append(".")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = sourceCodeText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { offset ->
                    sourceCodeText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
                
                // Contact link (bildirin only)
                val contactText = buildAnnotatedString {
                    append(stringResource(id = R.string.about_feedback))
                    pushStringAnnotation(tag = "URL", annotation = "https://wa.me/905392552070")
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold
                    )) {
                        append(stringResource(id = R.string.about_report))
                    }
                    pop()
                    append(".")
                }

                @Suppress("DEPRECATION")
                ClickableText(
                    text = contactText,
                    style = TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { offset ->
                    contactText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            }
        },
        confirmButton = {}
    )
}
