package com.avalibeyaz.evrak

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avalibeyaz.evrak.data.Evrak
import com.avalibeyaz.evrak.data.EvrakDatabase
import com.avalibeyaz.evrak.data.EvrakRepository
import com.avalibeyaz.evrak.data.CleanupManager
import com.avalibeyaz.evrak.ui.EvrakFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EvrakRepository
    val historyList: StateFlow<List<Evrak>>

    private val _selectedFilter = MutableStateFlow(EvrakFilter.ALL)
    val selectedFilter: StateFlow<EvrakFilter> = _selectedFilter.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("evrak_prefs", Context.MODE_PRIVATE)
    private val _powerPointEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("exp_powerpoint", false)
    )
    val powerPointEnabled: StateFlow<Boolean> = _powerPointEnabled.asStateFlow()

    fun setPowerPointEnabled(enabled: Boolean) {
        _powerPointEnabled.value = enabled
        sharedPrefs.edit().putBoolean("exp_powerpoint", enabled).apply()
    }
    private val _folderSelectionEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("folder_selection_enabled", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    )
    val folderSelectionEnabled: StateFlow<Boolean> = _folderSelectionEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(
        sharedPrefs.getString("theme_mode", "system") ?: "system"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    init {
        val database = EvrakDatabase.getDatabase(application)
        repository = EvrakRepository(application, database.evrakDao())
        historyList = kotlinx.coroutines.flow.combine(
            repository.allEvraklar,
            _powerPointEnabled
        ) { evraklar, pptEnabled ->
            if (pptEnabled) {
                evraklar
            } else {
                evraklar.filter { !it.path.endsWith(".ppt", true) && !it.path.endsWith(".pptx", true) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        viewModelScope.launch {
            CleanupManager.performCleanup(application, repository)
        }
    }

    fun disableFolderSelection() {
        _folderSelectionEnabled.value = false
        sharedPrefs.edit().putBoolean("folder_selection_enabled", false).apply()
    }

    fun openDocument(
        uri: Uri, 
        resolver: ContentResolver? = null, 
        onError: (String) -> Unit = {},
        onOpened: (Evrak) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val evrak = repository.addEvrakFromUri(uri, resolver)
                if (evrak != null) {
                    onOpened(evrak)
                } else {
                    onError(getApplication<Application>().getString(R.string.error_unknown))
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: getApplication<Application>().getString(R.string.error_unknown))
            }
        }
    }

    fun updateEvrakTimestamp(evrak: Evrak) {
        viewModelScope.launch {
            repository.updateEvrakTimestamp(evrak)
        }
    }

    fun deleteEvrak(evrak: Evrak) {
        viewModelScope.launch {
            repository.deleteEvrak(evrak)
        }
    }

    fun renameEvrak(evrak: Evrak, newName: String, onRenamed: ((Evrak) -> Unit)? = null) {
        viewModelScope.launch {
            val updated = repository.renameEvrak(evrak, newName)
            onRenamed?.invoke(updated)
        }
    }

    fun deleteAllEvrak() {
        viewModelScope.launch {
            repository.deleteAllEvrak()
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            val currentList = historyList.value
            currentList.forEach { evrak ->
                if (!java.io.File(evrak.path).exists()) {
                    repository.deleteEvrak(evrak)
                }
            }
        }
    }

    fun setFilter(filter: EvrakFilter) {
        _selectedFilter.value = filter
    }
}
