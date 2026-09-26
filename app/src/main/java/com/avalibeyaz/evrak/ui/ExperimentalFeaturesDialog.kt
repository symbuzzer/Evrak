package com.avalibeyaz.evrak.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avalibeyaz.evrak.MainViewModel
import com.avalibeyaz.evrak.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExperimentalFeaturesDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("evrak_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    var pptEnabled by remember { mutableStateOf(prefs.getBoolean("exp_powerpoint", false)) }
    val themeMode by viewModel.themeMode.collectAsState()
    var isChangingLanguage by remember { mutableStateOf(false) }

    if (isChangingLanguage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

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

                ExperimentalToggleItem(
                    label = stringResource(id = R.string.exp_powerpoint),
                    checked = pptEnabled,
                    onCheckedChange = {
                        pptEnabled = it
                        viewModel.setPowerPointEnabled(it)
                    }
                )

                ExperimentalThemeItem(
                    currentThemeMode = themeMode,
                    onThemeModeSelected = {
                        viewModel.setThemeMode(it)
                    }
                )

                val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                val currentTag = if (currentLocales.isEmpty) "" else currentLocales.toLanguageTags()
                ExperimentalLanguageItem(
                    currentTag = currentTag,
                    onLanguageSelected = { tag ->
                        isChangingLanguage = true
                        coroutineScope.launch {
                            delay(400)
                            val locales = if (tag.isEmpty()) {
                                androidx.core.os.LocaleListCompat.forLanguageTags("")
                            } else {
                                androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                            }
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                            context.findActivity()?.recreate()
                        }
                    }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ExperimentalThemeItem(
    currentThemeMode: String,
    onThemeModeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val themes = listOf(
        "system" to stringResource(id = R.string.theme_system),
        "dark" to stringResource(id = R.string.theme_dark),
        "light" to stringResource(id = R.string.theme_light)
    )

    val currentLabel = themes.find { it.first == currentThemeMode }?.second ?: stringResource(id = R.string.theme_system)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.exp_theme),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(text = currentLabel, style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                themes.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(text = label) },
                        onClick = {
                            expanded = false
                            onThemeModeSelected(mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalLanguageItem(
    currentTag: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val languages = listOf(
        "" to stringResource(id = R.string.language_default),
        "tr" to stringResource(id = R.string.language_turkish),
        "en" to stringResource(id = R.string.language_english)
    )

    val currentLabel = languages.find { it.first == currentTag }?.second ?: stringResource(id = R.string.language_default)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.exp_language),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(text = currentLabel, style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text = { Text(text = label) },
                        onClick = {
                            expanded = false
                            onLanguageSelected(tag)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
