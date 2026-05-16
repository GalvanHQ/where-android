package com.ovi.where.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.data.local.dao.MessageDao
import com.ovi.where.data.local.entity.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI model representing a media item in the gallery grid.
 */
data class MediaItemUiModel(
    val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val type: MediaItemType,
    val timestamp: Long,
    val senderName: String
)

enum class MediaItemType { IMAGE, VIDEO }

/**
 * UI model representing a document/file item in the gallery.
 */
data class FileItemUiModel(
    val id: String,
    val name: String,
    val url: String,
    val timestamp: Long,
    val senderName: String
)

enum class MediaGalleryTab { MEDIA, FILES }

data class MediaGalleryUiState(
    val selectedTab: MediaGalleryTab = MediaGalleryTab.MEDIA,
    val mediaItems: List<MediaItemUiModel> = emptyList(),
    val fileItems: List<FileItemUiModel> = emptyList(),
    val isLoadingMedia: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val hasMoreMedia: Boolean = true,
    val hasMoreFiles: Boolean = true,
    val fullScreenViewerIndex: Int? = null,
    val conversationTitle: String = ""
)

/**
 * ViewModel for the MediaGallery screen.
 *
 * Fetches media (IMAGE/VIDEO) and document messages from Room,
 * paginated at 30 items per page, sorted by timestamp descending.
 *
 * Requirements: 25.1, 25.2, 25.3, 25.4, 25.5, 25.6, 25.7
 */
@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""

    private val _uiState = MutableStateFlow(MediaGalleryUiState())
    val uiState: StateFlow<MediaGalleryUiState> = _uiState.asStateFlow()

    private var mediaPage = 0
    private var filesPage = 0

    companion object {
        private const val PAGE_SIZE = 30
    }

    init {
        loadNextMediaPage()
    }

    fun selectTab(tab: MediaGalleryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            MediaGalleryTab.MEDIA -> {
                if (_uiState.value.mediaItems.isEmpty() && !_uiState.value.isLoadingMedia) {
                    mediaPage = 0
                    loadNextMediaPage()
                }
            }
            MediaGalleryTab.FILES -> {
                if (_uiState.value.fileItems.isEmpty() && !_uiState.value.isLoadingFiles) {
                    filesPage = 0
                    loadNextFilesPage()
                }
            }
        }
    }

    fun loadNextMediaPage() {
        if (_uiState.value.isLoadingMedia || !_uiState.value.hasMoreMedia) return
        _uiState.update { it.copy(isLoadingMedia = true) }

        viewModelScope.launch {
            val offset = mediaPage * PAGE_SIZE
            val entities = messageDao.getMediaMessages(conversationId, PAGE_SIZE, offset)
            val items = entities.map { it.toMediaItemUiModel() }

            _uiState.update { state ->
                state.copy(
                    mediaItems = state.mediaItems + items,
                    isLoadingMedia = false,
                    hasMoreMedia = items.size == PAGE_SIZE
                )
            }
            mediaPage++
        }
    }

    fun loadNextFilesPage() {
        if (_uiState.value.isLoadingFiles || !_uiState.value.hasMoreFiles) return
        _uiState.update { it.copy(isLoadingFiles = true) }

        viewModelScope.launch {
            val offset = filesPage * PAGE_SIZE
            val entities = messageDao.getDocumentMessages(conversationId, PAGE_SIZE, offset)
            val items = entities.map { it.toFileItemUiModel() }

            _uiState.update { state ->
                state.copy(
                    fileItems = state.fileItems + items,
                    isLoadingFiles = false,
                    hasMoreFiles = items.size == PAGE_SIZE
                )
            }
            filesPage++
        }
    }

    fun openFullScreenViewer(index: Int) {
        _uiState.update { it.copy(fullScreenViewerIndex = index) }
    }

    fun closeFullScreenViewer() {
        _uiState.update { it.copy(fullScreenViewerIndex = null) }
    }

    fun navigateViewer(direction: Int) {
        val current = _uiState.value.fullScreenViewerIndex ?: return
        val newIndex = current + direction
        if (newIndex in _uiState.value.mediaItems.indices) {
            _uiState.update { it.copy(fullScreenViewerIndex = newIndex) }
        }
    }

    private fun MessageEntity.toMediaItemUiModel(): MediaItemUiModel {
        val itemType = when (type.uppercase()) {
            "VIDEO" -> MediaItemType.VIDEO
            else -> MediaItemType.IMAGE
        }
        return MediaItemUiModel(
            id = id,
            url = imageUrl ?: "",
            thumbnailUrl = thumbnailUrl,
            type = itemType,
            timestamp = timestamp,
            senderName = senderName
        )
    }

    private fun MessageEntity.toFileItemUiModel(): FileItemUiModel {
        return FileItemUiModel(
            id = id,
            name = text.ifBlank { "Document" },
            url = imageUrl ?: "",
            timestamp = timestamp,
            senderName = senderName
        )
    }
}
