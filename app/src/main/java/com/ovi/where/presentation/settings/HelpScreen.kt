package com.ovi.where.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

private const val SUPPORT_EMAIL = "support@whereapp.com"

private data class FaqEntry(
    val question: String,
    val answer: String
)

private val FAQ_ENTRIES = listOf(
    FaqEntry(
        question = "How do I share my location with friends?",
        answer = "Go to the Map tab and tap the location sharing button. You can choose to share with all friends or specific groups. Your location will be visible to selected people in real time."
    ),
    FaqEntry(
        question = "How do I create or join a group?",
        answer = "Navigate to the People tab and tap the + button to create a new group. To join an existing group, ask a group member for the invite code and use the 'Join Group' option."
    ),
    FaqEntry(
        question = "How do I change my privacy settings?",
        answer = "Go to Settings > Privacy. There you can control who sees your location and who can find your profile. Changes take effect immediately."
    ),
    FaqEntry(
        question = "Why is my location not updating?",
        answer = "Make sure location permissions are granted in your device settings. Also check that battery optimization is not restricting the app. Go to Settings > App Info > Battery and select 'Unrestricted'."
    ),
    FaqEntry(
        question = "How do I delete my account?",
        answer = "Go to Settings > Account Security > Delete Account. This action is permanent and cannot be undone. All your data, groups, and conversations will be removed."
    ),
    FaqEntry(
        question = "Can I use Where without an internet connection?",
        answer = "You can view cached data (messages, groups, profiles) while offline. New messages and location updates will be queued and sent when you reconnect."
    )
)

/**
 * Help & Support screen displaying FAQ entries in an expandable list
 * and a "Contact Support" button that opens the device email client.
 *
 * Requirements: 8.9
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val expandedItems = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Help & Support",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spaceLarge)
        ) {
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // ── FAQ section ──────────────────────────────────────────────
            Text(
                text = "Frequently Asked Questions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spaceSmall)
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column {
                    FAQ_ENTRIES.forEachIndexed { index, entry ->
                        FaqItem(
                            entry = entry,
                            isExpanded = expandedItems[index] == true,
                            onClick = {
                                expandedItems[index] = !(expandedItems[index] ?: false)
                            }
                        )
                        if (index < FAQ_ENTRIES.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = Dimens.spaceLarge),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            // ── Contact Support section ──────────────────────────────────
            Text(
                text = "Still need help?",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spaceSmall)
            )

            Spacer(modifier = Modifier.height(Dimens.spaceMedium))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$SUPPORT_EMAIL")
                        putExtra(Intent.EXTRA_SUBJECT, "Where App - Support Request")
                    }
                    context.startActivity(Intent.createChooser(intent, "Contact Support"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.Email,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.spaceMedium)
                )
                Text("Contact Support")
            }

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))
        }
    }
}

@Composable
private fun FaqItem(
    entry: FaqEntry,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(Dimens.spaceMedium))
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = entry.answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.spaceMedium)
            )
        }
    }
}
