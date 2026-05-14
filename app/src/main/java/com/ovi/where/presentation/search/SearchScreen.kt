package com.ovi.where.presentation.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.LIST_ITEM_ANIMATION_DURATION_MS
import com.ovi.where.presentation.common.search.SuggestionUiModel
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.FriendshipActionUiModel
import com.ovi.where.presentation.model.SearchUserUiModel
import com.ovi.where.presentation.people.components.FriendshipActionPill

/**
 * Full-screen Messenger-style search screen.
 *
 * Layout:
 * - Top bar: Back arrow + pill-shaped search field (auto-focused)
 * - Initial state (no query): Recent searches grid + Suggested vertical list
 * - Active search: Results list with loading indicator
 * - Empty state: Centered icon + message when no results found
 */
@Composable
fun SearchScreen(
    source: String,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchUiState by viewModel.searchUiState.collectAsState()
    val query = searchUiState.query
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field when the screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        // ── Top Bar: Back + Search Pill ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimens.spaceSmall,
                    end = Dimens.spaceLarge,
                    top = Dimens.spaceMedium,
                    bottom = Dimens.spaceMedium
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Dimens.cornerRound),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SEARCH_BAR_HEIGHT)
                        .padding(horizontal = Dimens.spaceLarge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSizeMedium)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.spaceMedium),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (source == "people") "Search people..." else "Search chats...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alpha(PLACEHOLDER_ALPHA)
                            )
                        }

                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::onQueryChanged,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val trimmed = query.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.onQuerySubmitted(trimmed)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }

                    // Clear button
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = viewModel::onClearQuery,
                            modifier = Modifier.size(CLEAR_BUTTON_SIZE)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimens.iconSizeMedium)
                            )
                        }
                    }
                }
            }
        }

        // ── Loading indicator (slim linear below search bar) ────────────────
        AnimatedVisibility(
            visible = searchUiState.isLoading && query.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PROGRESS_INDICATOR_HEIGHT),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ── Content ─────────────────────────────────────────────────────────
        when {
            // No query: show recent searches + suggested (or empty if cleared)
            query.isBlank() -> {
                IdleContent(
                    suggestions = searchUiState.suggestions,
                    onClearAllRecentSearches = viewModel::onClearAllRecentSearches,
                    onSuggestionTapped = { suggestion ->
                        when (source) {
                            "people" -> onNavigateToUserProfile(suggestion.userId)
                            "chats" -> {
                                viewModel.getOrCreateDirectChat(suggestion.userId) { conversationId ->
                                    conversationId?.let { onNavigateToChat(it) }
                                }
                            }
                        }
                    }
                )
            }
            // Results available
            searchUiState.searchResults.isNotEmpty() -> {
                when (source) {
                    "people" -> PeopleSearchResults(
                        results = searchUiState.searchResults,
                        onNavigateToUserProfile = onNavigateToUserProfile,
                        onSendFriendRequest = viewModel::sendFriendRequest
                    )
                    "chats" -> ChatsSearchResults(
                        results = searchUiState.searchResults,
                        onNavigateToChat = onNavigateToChat
                    )
                }
            }
            // Loading: waiting for debounce or data
            searchUiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            // Empty state: query entered, not loading, no results
            else -> {
                SearchEmptyState()
            }
        }
    }
}

// ── Idle Content (Recent Searches + Suggested) ───────────────────────────────

@Composable
private fun IdleContent(
    suggestions: List<SuggestionUiModel>,
    onClearAllRecentSearches: () -> Unit,
    onSuggestionTapped: (SuggestionUiModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.spaceLarge)
    ) {
        // ── Recent Searches Section (avatar grid) ────────────────────────────
        if (suggestions.isNotEmpty()) {
            item(key = "recent_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Dimens.spaceLarge,
                            end = Dimens.spaceLarge,
                            top = Dimens.spaceLarge,
                            bottom = Dimens.spaceMedium
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onClearAllRecentSearches) {
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item(key = "recent_grid") {
                RecentSearchesGrid(
                    suggestions = suggestions.take(MAX_RECENT_GRID_ITEMS),
                    onSuggestionTapped = onSuggestionTapped
                )
            }
        }

        // ── Suggested Section (vertical list) ────────────────────────────────
        if (suggestions.isNotEmpty()) {
            item(key = "suggested_header") {
                Text(
                    text = "Suggested",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        start = Dimens.spaceLarge,
                        end = Dimens.spaceLarge,
                        top = Dimens.spaceXLarge,
                        bottom = Dimens.spaceMedium
                    )
                )
            }

            items(
                items = suggestions,
                key = { "suggested_${it.userId}" }
            ) { suggestion ->
                SuggestedRow(
                    suggestion = suggestion,
                    onTapped = { onSuggestionTapped(suggestion) }
                )
            }
        }
    }
}

// ── Recent Searches Grid ─────────────────────────────────────────────────────

@Composable
private fun RecentSearchesGrid(
    suggestions: List<SuggestionUiModel>,
    onSuggestionTapped: (SuggestionUiModel) -> Unit
) {
    // Using a fixed-height grid inside the LazyColumn item
    val rows = (suggestions.size + GRID_COLUMNS - 1) / GRID_COLUMNS
    val gridHeight = (GRID_ITEM_HEIGHT * rows) + (Dimens.spaceMedium * (rows - 1).coerceAtLeast(0))

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight)
            .padding(horizontal = Dimens.spaceLarge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        userScrollEnabled = false
    ) {
        items(
            items = suggestions,
            key = { "grid_${it.userId}" }
        ) { suggestion ->
            RecentSearchGridItem(
                suggestion = suggestion,
                onTapped = { onSuggestionTapped(suggestion) }
            )
        }
    }
}

@Composable
private fun RecentSearchGridItem(
    suggestion: SuggestionUiModel,
    onTapped: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onTapped)
            .padding(vertical = Dimens.spaceSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular avatar
        Box(
            modifier = Modifier
                .size(AVATAR_GRID_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (suggestion.photoUrl != null) {
                AsyncImage(
                    model = suggestion.photoUrl,
                    contentDescription = suggestion.displayName,
                    modifier = Modifier
                        .size(AVATAR_GRID_SIZE)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initial = suggestion.displayName.take(1).uppercase().ifEmpty { "?" }
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        // Name below avatar
        Text(
            text = suggestion.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Suggested Row ────────────────────────────────────────────────────────────

@Composable
private fun SuggestedRow(
    suggestion: SuggestionUiModel,
    onTapped: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTapped)
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular avatar (40dp)
        Box(
            modifier = Modifier
                .size(AVATAR_SUGGESTED_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (suggestion.photoUrl != null) {
                AsyncImage(
                    model = suggestion.photoUrl,
                    contentDescription = suggestion.displayName,
                    modifier = Modifier
                        .size(AVATAR_SUGGESTED_SIZE)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initial = suggestion.displayName.take(1).uppercase().ifEmpty { "?" }
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(Dimens.spaceLarge))

        Text(
            text = suggestion.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Search Results ───────────────────────────────────────────────────────────

@Composable
private fun PeopleSearchResults(
    results: List<Any>,
    onNavigateToUserProfile: (String) -> Unit,
    onSendFriendRequest: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.spaceMedium)
    ) {
        items(
            items = results,
            key = { item -> (item as SearchUserUiModel).userId }
        ) { item ->
            val user = item as SearchUserUiModel
            SearchResultRow(
                user = user,
                onRowTap = { onNavigateToUserProfile(user.userId) },
                onPillTap = {
                    if (user.friendshipAction == FriendshipActionUiModel.ADD) {
                        onSendFriendRequest(user.userId)
                    }
                },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                    placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                    fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                )
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    user: SearchUserUiModel,
    onRowTap: () -> Unit,
    onPillTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onRowTap)
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(Dimens.avatarSizeMedium)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoUrl != null) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = user.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = user.avatarInitial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(Dimens.spaceLarge))

        // Name + username
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (user.username.isNotEmpty()) {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Friendship action pill
        FriendshipActionPill(
            action = user.friendshipAction,
            onTap = onPillTap
        )
    }
}

@Composable
private fun ChatsSearchResults(
    results: List<Any>,
    onNavigateToChat: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.spaceMedium)
    ) {
        items(
            items = results,
            key = { (it as ConversationUiModel).id }
        ) { item ->
            val conversation = item as ConversationUiModel
            Column(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                    placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                    fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                )
            ) {
                ChatSearchResultRow(
                    conversation = conversation,
                    onClick = { onNavigateToChat(conversation.id) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = Dimens.dividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ── Chat Search Result Row ───────────────────────────────────────────────────

@Composable
private fun ChatSearchResultRow(
    conversation: ConversationUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(Dimens.avatarSizeMedium)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!conversation.photoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = conversation.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = conversation.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(Dimens.spaceLarge))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Dimens.spaceXSmall))
            Text(
                text = conversation.lastMessageText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun SearchEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = Dimens.space3XLarge),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.searching),
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .alpha(EMPTY_STATE_ICON_ALPHA),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))
            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Constants ────────────────────────────────────────────────────────────────

private const val PLACEHOLDER_ALPHA = 0.6f
private const val EMPTY_STATE_ICON_ALPHA = 0.9f
private const val GRID_COLUMNS = 5
private const val MAX_RECENT_GRID_ITEMS = 10
private val SEARCH_BAR_HEIGHT = 48.dp
private val PROGRESS_INDICATOR_HEIGHT = 2.dp
private val CLEAR_BUTTON_SIZE = 36.dp
private val AVATAR_GRID_SIZE = 56.dp
private val AVATAR_SUGGESTED_SIZE = 40.dp
private val GRID_ITEM_HEIGHT = 90.dp
