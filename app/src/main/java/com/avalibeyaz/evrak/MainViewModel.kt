package com.avalibeyaz.evrak

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avalibeyaz.evrak.data.Evrak
import com.avalibeyaz.evrak.data.EvrakDatabase
import com.avalibeyaz.evrak.data.EvrakRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EvrakRepository
    val historyList: StateFlow<List<Evrak>>

    init {
        val database = EvrakDatabase.getDatabase(application)
        repository = EvrakRepository(application, database.evrakDao())
        historyList = repository.allEvraklar.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun openDocument(uri: Uri, resolver: ContentResolver? = null, onOpened: (Evrak) -> Unit) {
        viewModelScope.launch {
            val evrak = repository.addEvrakFromUri(uri, resolver)
            if (evrak != null) {
                onOpened(evrak)
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
