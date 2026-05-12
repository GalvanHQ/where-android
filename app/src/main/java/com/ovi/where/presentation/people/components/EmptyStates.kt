package com.ovi.where.presentation.people.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ovi.where.core.theme.Dimens

/**
 * Empty state for the People tab when user has no friends yet.
 */
@Composable
fun PeopleEmptyState(
    onFindFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeXXLarge),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = "Find your people",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Add friends to see them here and share your location with each other.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceXLarge))
        Button(onClick = onFindFriends) {
            Text("Find Friends")
        }
    }
}

/**
 * Empty state for the Requests screen tabs.
 */
@Composable
fun RequestsEmptyState(
    tab: String,
    modifier: Modifier = Modifier
) {
    val message = when (tab.lowercase()) {
        "incoming" -> "No incoming friend requests right now."
        "outgoing" -> "You haven't sent any friend requests."
        else -> "No requests to show."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeXLarge),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Pre-search empty state: "Start typing to find people".
 */
@Composable
fun SearchPreEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.PersonSearch,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeXLarge),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = "Start typing to find people",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * No results state for search: "No users found for '...'" + invite CTA.
 */
@Composable
fun SearchNoResultsState(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeXLarge),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = "No users found for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceMedium))
        TextButton(onClick = { /* invite flow placeholder */ }) {
            Text("Invite a friend")
        }
    }
}

/**
 * Error state for profile screen with retry.
 */
@Composable
fun ProfileErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeXLarge),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/**
 * Profile not found state with back navigation.
 */
@Composable
fun ProfileNotFoundState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeXLarge),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = "User not found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceMedium))
        Text(
            text = "This profile may no longer exist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceXLarge))
        Button(onClick = onBack) {
            Text("Go Back")
        }
    }
}
