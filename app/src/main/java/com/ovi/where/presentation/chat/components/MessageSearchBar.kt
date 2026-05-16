package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Search bar for in-conversation message search.
 *
 * Displays a text input (max 100 chars), result position indicator ("3 of 10"),
 * up/down navigation arrows, and a dismiss (X) button.
 *
 * Requirements: 13.1, 13.4, 13.5, 13.6
 *
 * @param query Current search query text.
 * @param onQueryChange Called when the user types in the search field.
 * @param resultCount Total number of search results.
 * @param currentResultIndex Current focused result index (0-based), -1 if no results.
 * @param onNavigateUp Navigate to previous result (up arrow).
 * @param onNavigateDown Navigate to next result (down arrow).
 * @param onDismiss Dismiss the search bar (X button or back).
 */
@Composable
fun MessageSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
    currentResultIndex: Int,
    onNavigateUp: () -> Unit,
    onNavigateDown: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the search field when it appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search text field
            OutlinedTextField(
                value = query,
                onValueChange = { newValue ->
                    // Requirement 13.1: Maximum 100 characters
                    if (newValue.length <= 100) {
                        onQueryChange(newValue)
                    }
                },
                placeholder = { Text("Search messages") },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                )
            )

            Spacer(Modifier.width(8.dp))

            // Result position indicator or "No results" text
            // Requirement 13.1: Display "3 of 10" position indicator
            // Requirement 13.6: Display "No results" when no matches found
            if (query.length >= 2) {
                if (resultCount > 0) {
                    Text(
                        text = "${currentResultIndex + 1} of $resultCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Up arrow — navigate to previous result
            // Requirement 13.5: Disabled at first result
            IconButton(
                onClick = onNavigateUp,
                enabled = resultCount > 0 && currentResultIndex > 0
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Previous result",
                    modifier = Modifier.size(24.dp),
                    tint = if (resultCount > 0 && currentResultIndex > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            // Down arrow — navigate to next result
            // Requirement 13.4: Disabled at last result
            IconButton(
                onClick = onNavigateDown,
                enabled = resultCount > 0 && currentResultIndex < resultCount - 1
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next result",
                    modifier = Modifier.size(24.dp),
                    tint = if (resultCount > 0 && currentResultIndex < resultCount - 1) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            // Dismiss button (X)
            // Requirement 13.7: On dismiss, remove highlights, restore scroll position
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
