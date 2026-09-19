package com.avalibeyaz.evrak.ui

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avalibeyaz.evrak.R

@Composable
fun MarqueeTitle(
    title: String,
    modifier: Modifier = Modifier,
    onRenameClick: ((String) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(
                    if (onRenameClick != null) {
                        Modifier.clickable { showMenu = true }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    velocity = 40.dp
                )
            )
        }

        if (onRenameClick != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = {
                        showMenu = false
                        showRenameDialog = true
                    }
                )
            }

            if (showRenameDialog) {
                val initialName = title.substringBeforeLast(".")
                var textFieldValue by remember {
                    mutableStateOf(
                        TextFieldValue(
                            text = initialName,
                            selection = TextRange(0, initialName.length)
                        )
                    )
                }
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text(text = stringResource(id = R.string.rename_title)) },
                    text = {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            label = { Text(text = stringResource(id = R.string.rename_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (textFieldValue.text.isNotBlank()) {
                                    onRenameClick(textFieldValue.text)
                                }
                                showRenameDialog = false
                            }
                        ) {
                            Text(text = stringResource(id = R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
