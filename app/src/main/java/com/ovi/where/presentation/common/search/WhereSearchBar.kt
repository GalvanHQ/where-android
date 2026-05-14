package com.ovi.where.presentation.common.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.core.utils.effectiveAnimationDuration
import kotlinx.coroutines.delay

/**
 * Messenger-style always-visible search bar rendered as a rounded pill.
 *
 * Displays a leading search icon, placeholder text, and a text input field.
 * Animates elevation and background on focus change with a 200ms ease-in-out tween.
 * Uses Material 3 color tokens for seamless light/dark theme support.
 *
 * Additional features (clear button, loading indicator, recent searches, suggestions,
 * and results content) are wired in subsequent tasks.
 */
@Composable
fun WhereSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onQuerySubmitted: (String) -> Unit,
    onClearQuery: () -> Unit,
    placeholderText: String,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    isLoading: Boolean = false,
    recentSearches: List<String> = emptyList(),
    onRecentSearchTapped: (String) -> Unit = {},
    onRecentSearchDeleted: (String) -> Unit = {},
    onClearAllRecentSearches: () -> Unit = {},
    suggestions: List<SuggestionUiModel> = emptyList(),
    onSuggestionTapped: (SuggestionUiModel) -> Unit = {},
    showEmptyState: Boolean = false,
    modifier: Modifier = Modifier,
    resultsContent: @Composable (() -> Unit)? = null
) {
    val reducedMotion = LocalReducedMotion.current
    val animationDuration = effectiveAnimationDuration(
        normalDurationMs = FOCUS_ANIMATION_DURATION_MS,
        reducedMotion = reducedMotion
    )

    // Animate elevation: subtle lift when focused
    val elevation by animateDpAsState(
        targetValue = if (isFocused) Dimens.cardElevationSubtle else 0.dp,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "searchBarElevation"
    )

    // Animated background color: surfaceContainerHigh always, elevation provides visual lift
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.cornerRound),
            color = backgroundColor,
            shadowElevation = elevation,
            tonalElevation = elevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SEARCH_BAR_HEIGHT)
                    .padding(horizontal = Dimens.spaceLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading search icon
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSizeMedium)
                )

                // Text field with placeholder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Dimens.spaceMedium),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Placeholder text (visible when query is empty)
                    if (query.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(PLACEHOLDER_ALPHA)
                        )
                    }

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = contentColor
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val trimmed = query.trim()
                                if (trimmed.isNotEmpty()) {
                                    onQuerySubmitted(trimmed)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Trailing clear button — animated fade-in/scale when query is non-empty
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    IconButton(
                        onClick = onClearQuery,
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

        // Slim linear progress indicator below the pill when loading
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge)
                    .height(PROGRESS_INDICATOR_HEIGHT),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Expandable dropdown content: recent searches + suggestions
        // Shown when focused and query is empty (no active search)
        AnimatedVisibility(
            visible = isFocused && query.isEmpty(),
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceMedium)
            ) {
                // Recent searches chips section
                RecentSearchesSection(
                    recentSearches = recentSearches,
                    onRecentSearchTapped = onRecentSearchTapped,
                    onRecentSearchDeleted = onRecentSearchDeleted,
                    onClearAllRecentSearches = onClearAllRecentSearches
                )

                // Suggestions row section
                SuggestionsRow(
                    suggestions = suggestions,
                    onSuggestionTapped = onSuggestionTapped
                )
            }
        }

        // Results content slot / empty state: shown when query is non-empty
        // Uses Crossfade for a smooth transition between results and empty state
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(durationMillis = CROSSFADE_DURATION_MS)),
            exit = fadeOut(animationSpec = tween(durationMillis = CROSSFADE_DURATION_MS))
        ) {
            Crossfade(
                targetState = resultsContent != null && !showEmptyState,
                animationSpec = tween(durationMillis = CROSSFADE_DURATION_MS),
                label = "resultsContentCrossfade"
            ) { hasResults ->
                if (hasResults && resultsContent != null) {
                    resultsContent()
                } else if (showEmptyState) {
                    SearchEmptyState()
                }
            }
        }
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

private const val FOCUS_ANIMATION_DURATION_MS = 200
private const val PLACEHOLDER_ALPHA = 0.6f
private const val CROSSFADE_DURATION_MS = 300
private const val EMPTY_STATE_ALPHA = 0.6f
private const val CHIP_ANIMATION_DURATION_MS = 300
private val SEARCH_BAR_HEIGHT = 48.dp
private val PROGRESS_INDICATOR_HEIGHT = 2.dp
private val CLEAR_BUTTON_SIZE = 36.dp
private val CHIP_DELETE_ICON_SIZE = 18.dp

// ── Recent Searches Section ──────────────────────────────────────────────────

/**
 * Displays a horizontally-scrollable LazyRow of recent search chips with a header.
 * Hidden entirely when the list is empty.
 *
 * Each chip shows the search text and a trailing delete icon. Deletion triggers
 * a shrink-and-fade animation via `animateItem()` on the LazyRow items.
 * Tapping a chip invokes `onRecentSearchTapped(query)`.
 */
@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onRecentSearchTapped: (String) -> Unit,
    onRecentSearchDeleted: (String) -> Unit,
    onClearAllRecentSearches: () -> Unit
) {
    if (recentSearches.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row: "Recent" label + "Clear all" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onClearAllRecentSearches) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Horizontally-scrollable chips row using LazyRow for smooth removal animations
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            items(
                count = recentSearches.size,
                key = { index -> recentSearches[index] }
            ) { index ->
                val searchQuery = recentSearches[index]
                InputChip(
                    selected = false,
                    onClick = { onRecentSearchTapped(searchQuery) },
                    label = {
                        Text(
                            text = searchQuery,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRecentSearchDeleted(searchQuery) },
                            modifier = Modifier.size(CHIP_DELETE_ICON_SIZE)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $searchQuery",
                                modifier = Modifier.size(Dimens.iconSizeXSmall)
                            )
                        }
                    },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(CHIP_ANIMATION_DURATION_MS),
                        fadeOutSpec = tween(CHIP_ANIMATION_DURATION_MS),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                )
            }
        }
    }
}

// ── Suggestions Row ──────────────────────────────────────────────────────────

private const val SUGGESTION_STAGGER_DELAY_MS = 50L
private val ONLINE_DOT_SIZE = 8.dp

/**
 * Horizontal scrollable row of circular user avatars with names below.
 * Hidden entirely when the suggestions list is empty.
 * Items stagger-animate in (scale-up + fade-in, 50ms delay between items) on first appearance.
 */
@Composable
private fun SuggestionsRow(
    suggestions: List<SuggestionUiModel>,
    onSuggestionTapped: (SuggestionUiModel) -> Unit
) {
    if (suggestions.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        // Header
        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
        )

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        // Horizontal row of suggestion avatars
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimens.spaceLarge
            )
        ) {
            itemsIndexed(
                items = suggestions,
                key = { _, suggestion -> suggestion.userId }
            ) { index, suggestion ->
                SuggestionItem(
                    suggestion = suggestion,
                    index = index,
                    onTapped = { onSuggestionTapped(suggestion) }
                )
            }
        }
    }
}

/**
 * A single suggestion item: circular avatar with name below and optional online dot.
 * Stagger-animates in with scale-up + fade-in based on its index.
 */
@Composable
private fun SuggestionItem(
    suggestion: SuggestionUiModel,
    index: Int,
    onTapped: () -> Unit
) {
    // Stagger animation state
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * SUGGESTION_STAGGER_DELAY_MS)
        visible = true
    }

    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "suggestionScale_$index"
    )

    val alpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "suggestionAlpha_$index"
    )

    Column(
        modifier = Modifier
            .width(Dimens.avatarSizeMedium + Dimens.spaceLarge)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .clickable(onClick = onTapped),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with online dot
        Box(contentAlignment = Alignment.BottomEnd) {
            if (suggestion.photoUrl != null) {
                AsyncImage(
                    model = suggestion.photoUrl,
                    contentDescription = suggestion.displayName,
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Initial fallback
                val initial = suggestion.displayName.take(1).uppercase().ifEmpty { "?" }
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Online status dot
            if (suggestion.isOnline) {
                Box(
                    modifier = Modifier
                        .size(ONLINE_DOT_SIZE)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        // Display name
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

// ── Empty State ──────────────────────────────────────────────────────────────

/**
 * Centered empty state displayed when a search query produces zero results.
 * Shows a search-off icon and a "No results found" message using
 * `onSurfaceVariant` color with reduced opacity for a subtle appearance.
 */
@Composable
private fun SearchEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.space3XLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = "No results",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Dimens.iconSizeXLarge)
                    .alpha(EMPTY_STATE_ALPHA)
            )
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Text(
                text = "No results found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(EMPTY_STATE_ALPHA)
            )
        }
    }
}
