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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EvrakRepository
    val historyList: StateFlow<List<Evrak>>

    private val sharedPrefs = application.getSharedPreferences("evrak_prefs", Context.MODE_PRIVATE)
    private val _folderSelectionEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("folder_selection_enabled", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    )
    val folderSelectionEnabled: StateFlow<Boolean> = _folderSelectionEnabled.asStateFlow()

    init {
        val database = EvrakDatabase.getDatabase(application)
        repository = EvrakRepository(application, database.evrakDao())
        historyList = repository.allEvraklar.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
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
                    onError(getApplication<Application>().getString(R.string.unsupported_format_message))
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: getApplication<Application>().getString(R.string.error_unknown))
            }
        }
    }

    fun deleteEvrak(evrak: Evrak) {
        viewModelScope.launch {
            repository.deleteEvrak(evrak)
        }
    }

    fun renameEvrak(evrak: Evrak, newName: String) {
        viewModelScope.launch {
            repository.renameEvrak(evrak, newName)
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
}
