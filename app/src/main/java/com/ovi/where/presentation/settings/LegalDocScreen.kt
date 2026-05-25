package com.ovi.where.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar

/**
 * Reusable scaffold for static legal text pages (Terms of Service,
 * Privacy Policy). Both pages share the same chrome: top app bar with
 * back navigation + scrollable body of section blocks.
 *
 * No domain yet, so the pages live in-app and we never leave the app
 * to read them. Once a website exists, swap these for `Custom Tab`
 * launches in [AboutScreen] without touching this file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalDocScreen(
    title: String,
    sections: List<LegalSection>,
    lastUpdated: String,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = title,
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLarge),
        ) {
            Spacer(Modifier.height(Dimens.spaceSmall))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Last updated: $lastUpdated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            sections.forEach { section ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                ) {
                    Text(
                        text = section.heading,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Dimens.space2XLarge))
        }
    }
}

/** A single titled paragraph in a legal document. */
internal data class LegalSection(
    val heading: String,
    val body: String,
)
