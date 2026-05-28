package com.ovi.where.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * MediaGallery screen displaying all shared media and files in a conversation.
 *
 * Accessible from ChatScreen header (both 1:1 and group conversations).
 * Two tabs: "Media" (images/videos, default) and "Files" (documents).
 *
 * Requirements: 25.1, 25.2, 25.3, 25.4, 25.5, 25.6, 25.7
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    onNavigateBack: () -> Unit,
    viewModel: MediaGalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Full-screen viewer overlay
    if (uiState.fullScreenViewerIndex != null) {
        val allImageUrls = uiState.mediaItems.map { it.url }
        com.ovi.where.presentation.chat.components.FullScreenImageViewer(
            imageUrl = allImageUrls.getOrElse(uiState.fullScreenViewerIndex!!) { "" },
            onDismiss = { viewModel.closeFullScreenViewer() },
            imageUrls = allImageUrls,
            initialIndex = uiState.fullScreenViewerIndex!!
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Media & Files",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row: Media | Files
            MediaGalleryTabs(
                selectedTab = uiState.selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            // Tab content
            when (uiState.selectedTab) {
                MediaGalleryTab.MEDIA -> MediaTabContent(
                    items = uiState.mediaItems,
                    isLoading = uiState.isLoadingMedia,
                    hasMore = uiState.hasMoreMedia,
                    onItemTap = { index -> viewModel.openFullScreenViewer(index) },
                    onLoadMore = { viewModel.loadNextMediaPage() }
                )
                MediaGalleryTab.FILES -> FilesTabContent(
                    items = uiState.fileItems,
                    isLoading = uiState.isLoadingFiles,
                    hasMore = uiState.hasMoreFiles,
                    onLoadMore = { viewModel.loadNextFilesPage() }
                )
            }
        }
    }
}

@Composable
private fun MediaGalleryTabs(
    selectedTab: MediaGalleryTab,
    onTabSelected: (MediaGalleryTab) -> Unit
) {
    TabRow(
        selectedTabIndex = if (selectedTab == MediaGalleryTab.MEDIA) 0 else 1,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Tab(
            selected = selectedTab == MediaGalleryTab.MEDIA,
            onClick = { onTabSelected(MediaGalleryTab.MEDIA) },
            text = { Text("Media") }
        )
        Tab(
            selected = selectedTab == MediaGalleryTab.FILES,
            onClick = { onTabSelected(MediaGalleryTab.FILES) },
            text = { Text("Files") }
        )
    }
}

/**
 * Media tab: 3-column grid, timestamp descending, paginated (30 per page).
 * Requirement 25.2: 3-column grid sorted by timestamp descending, loading indicator at bottom.
 * Requirement 25.5: 240px width thumbnails in grid.
 * Requirement 25.6: Placeholder error icon on load failure, tap to retry.
 * Requirement 25.7: Empty state "No media shared yet".
 */
@Composable
private fun MediaTabContent(
    items: List<MediaItemUiModel>,
    isLoading: Boolean,
    hasMore: Boolean,
    onItemTap: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    if (items.isEmpty() && !isLoading) {
        // Requirement 25.7: Empty state
        EmptyStateMessage(text = "No media shared yet")
        return
    }

    val gridState = rememberLazyGridState()

    // Pagination: load more when near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            hasMore && !isLoading && lastVisibleItem >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.id }
        ) { index, item ->
            MediaGridItem(
                item = item,
                onClick = { onItemTap(index) }
            )
        }

        // Loading indicator at bottom (Requirement 25.2)
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * Single media grid item (thumbnail).
 * Requirement 25.5: 240px width thumbnails.
 * Requirement 25.6: Error placeholder with tap to retry.
 */
@Composable
private fun MediaGridItem(
    item: MediaItemUiModel,
    onClick: () -> Unit
) {
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable {
                if (loadFailed) {
                    loadFailed = false
                    retryKey++
                } else {
                    onClick()
                }
            }
            .semantics {
                contentDescription = if (item.type == MediaItemType.VIDEO) "Video from ${item.senderName}" else "Image from ${item.senderName}"
            }
    ) {
        if (loadFailed) {
            // Requirement 25.6: Placeholder error icon, tap to retry
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Failed to load",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to retry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val context = LocalContext.current
            val imageUrl = item.thumbnailUrl ?: item.url
            val cacheKey = "${item.id}_thumb_$imageUrl"

            val thumbnailModel = remember(imageUrl, retryKey) {
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(240, 240)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .setParameter("retry", retryKey)
                    .build()
            }

            SubcomposeAsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                },
                error = {
                    loadFailed = true
                }
            )

            // Video indicator overlay
            if (item.type == MediaItemType.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Files tab content: list of document items.
 * Requirement 25.7: Empty state "No files shared yet".
 */
@Composable
private fun FilesTabContent(
    items: List<FileItemUiModel>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (items.isEmpty() && !isLoading) {
        EmptyStateMessage(text = "No files shared yet")
        return
    }

    val gridState = rememberLazyGridState()

    // Pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            hasMore && !isLoading && lastVisibleItem >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        state = gridState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.id }
        ) { _, item ->
            FileListItem(item = item)
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FileListItem(item: FileItemUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = "Document",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.senderName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
