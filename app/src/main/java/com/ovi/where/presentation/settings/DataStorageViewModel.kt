package com.ovi.where.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DataStorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: com.ovi.where.data.local.db.AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataStorageUiState())
    val uiState: StateFlow<DataStorageUiState> = _uiState.asStateFlow()

    init {
        calculateCacheSize()
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                // cacheDir already includes Coil's disk cache (at cacheDir/image_cache),
                // so we only walk cacheDir to avoid double-counting.
                getDirSize(context.cacheDir)
            }
            _uiState.update { it.copy(cacheSizeBytes = size, isLoading = false) }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            withContext(Dispatchers.IO) {
                // Clear Room database (all tables — messages, conversations, etc.)
                appDatabase.clearAllTables()
                // Clear internal cache directory
                deleteDir(context.cacheDir)
                // Clear Coil image cache (both memory and disk)
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
            // Recalculate after clearing
            val newSize = withContext(Dispatchers.IO) {
                getDirSize(context.cacheDir)
            }
            _uiState.update {
                it.copy(
                    cacheSizeBytes = newSize,
                    isClearing = false
                )
            }
        }
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun deleteDir(dir: File) {
        if (!dir.exists()) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteDir(file)
            }
            file.delete()
        }
    }
}

data class DataStorageUiState(
    val cacheSizeBytes: Long = 0L,
    val isLoading: Boolean = true,
    val isClearing: Boolean = false
) {
    val formattedCacheSize: String
        get() = formatBytes(cacheSizeBytes)

    companion object {
        private const val BYTES_PER_KB = 1024L
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= BYTES_PER_GB -> String.format("%.2f GB", bytes.toDouble() / BYTES_PER_GB)
                bytes >= BYTES_PER_MB -> String.format("%.2f MB", bytes.toDouble() / BYTES_PER_MB)
                bytes >= BYTES_PER_KB -> String.format("%.2f KB", bytes.toDouble() / BYTES_PER_KB)
                else -> "$bytes B"
            }
        }
    }
}
