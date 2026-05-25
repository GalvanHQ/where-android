package com.ovi.where.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar

/**
 * Static profile of the developer behind Where. Lives on its own screen
 * (linked from Settings → About) so we don't pollute About with a sheet
 * and so the cards have room to breathe.
 *
 * Avatar is loaded from GitHub's stable profile-image URL — no bundled
 * drawable required, and the image updates if the developer changes
 * their profile photo. Coil falls back to the colored initials chip
 * if the network is unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopersScreen(
    onNavigateBack: () -> Unit,
) {
    val developers = remember { listOf(ISMAM) }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Developers",
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
            Spacer(Modifier.height(Dimens.spaceMedium))

            Text(
                text = "The team behind Where",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            developers.forEach { dev ->
                DeveloperCard(developer = dev)
            }

            Spacer(Modifier.height(Dimens.space2XLarge))
        }
    }
}

// ── Card ────────────────────────────────────────────────────────────────────

@Composable
private fun DeveloperCard(developer: Developer) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            // Header — avatar + name + role
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ovi),
                    contentDescription = "Ismam Hasan Ovi",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    alignment = Alignment.CenterStart,
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(Dimens.spaceLarge))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = developer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = developer.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                    )
                }
            }

            Text(
                text = developer.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )

            // Social row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val ctx = LocalContext.current

                SocialIconButton(
                    icon = ImageVector.vectorResource(id = R.drawable.github),
                    contentDescription = "GitHub",
                    onClick = { openUrl(ctx, developer.githubUrl, GITHUB_PACKAGE) },
                )
                SocialIconButton(
                    icon = ImageVector.vectorResource(id = R.drawable.linkedin),
                    contentDescription = "LinkedIn",
                    onClick = { openUrl(ctx, developer.linkedinUrl, LINKEDIN_PACKAGE) },
                )
                if (developer.facebookUrl != null) {
                    SocialIconButton(
                        icon = ImageVector.vectorResource(id = R.drawable.facebook),
                        contentDescription = "Facebook",
                        onClick = { openUrl(ctx, developer.facebookUrl, FACEBOOK_PACKAGE) },
                    )
                }
                if (developer.websiteUrl != null) {
                    SocialIconButton(
                        icon = Icons.Rounded.Language,
                        contentDescription = "Website",
                        onClick = { openUrl(ctx, developer.websiteUrl) },
                    )
                }
                SocialIconButton(
                    icon = Icons.Rounded.Email,
                    contentDescription = "Email",
                    onClick = { sendEmail(ctx, developer.email) },
                )
            }
        }
    }
}

@Composable
private fun SocialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

// Native-app package ids. We try these first when launching a profile so
// taps land in the user's installed Facebook / LinkedIn / GitHub client
// instead of a browser tab; fall back to the browser if the package isn't
// installed.
private const val FACEBOOK_PACKAGE = "com.facebook.katana"
private const val LINKEDIN_PACKAGE = "com.linkedin.android"
private const val GITHUB_PACKAGE = "com.github.android"
private const val GMAIL_PACKAGE = "com.google.android.gm"

/**
 * Opens [url] in [preferredPackage] when it's installed; otherwise falls
 * back to whatever app the user has set to handle web URLs (browser /
 * Custom Tabs). [preferredPackage] = null means "always use the system
 * default" (used for the website link, where there's no native client).
 */
private fun openUrl(
    context: android.content.Context,
    url: String,
    preferredPackage: String? = null,
) {
    val uri = url.toUri()
    if (preferredPackage != null) {
        val targeted = Intent(Intent.ACTION_VIEW, uri).setPackage(preferredPackage)
        try {
            context.startActivity(targeted)
            return
        } catch (_: ActivityNotFoundException) {
            // Native client isn't installed — drop the package hint and
            // let the system pick a handler (browser / Custom Tab).
        }
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

/**
 * Opens a "compose new email" intent. We hint at Gmail first because
 * that's the dominant Android client; if it isn't installed we fall back
 * to ACTION_SENDTO with no package, which surfaces the system email
 * chooser. Final fallback (no email apps at all) is a quiet no-op — the
 * email address is still discoverable visually via the icon's a11y label.
 */
private fun sendEmail(context: android.content.Context, email: String) {
    val mailto = "mailto:$email".toUri()
    val gmail = Intent(Intent.ACTION_SENDTO, mailto).setPackage(GMAIL_PACKAGE)
    try {
        context.startActivity(gmail)
        return
    } catch (_: ActivityNotFoundException) {
        // Gmail not installed — fall through to the system chooser.
    }
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, mailto))
    } catch (_: ActivityNotFoundException) {
        // No email app at all. Quiet no-op.
    }
}

// ── Data ────────────────────────────────────────────────────────────────────

private data class Developer(
    val name: String,
    val role: String,
    val description: String,
    val email: String,
    val avatarUrl: String,
    val githubUrl: String,
    val linkedinUrl: String,
    val facebookUrl: String? = null,
    val websiteUrl: String? = null,
)

private val ISMAM = Developer(
    name = "Ismam Hasan Ovi",
    role = "Android Developer",
    description = "Passionate Android developer focused on Jetpack Compose, " +
            "clean architecture, and Firebase. Built Where end-to-end — from " +
            "the realtime location pipeline to the chat layer and meetup flow.",
    email = "ismamhasanovi@gmail.com",
    // GitHub avatars are stable, 200x200, and fast to fetch — cheap default.
    avatarUrl = "https://github.com/oviii-001.png",
    githubUrl = "https://github.com/oviii-001",
    linkedinUrl = "https://www.linkedin.com/in/ismamovi",
    facebookUrl = "https://www.facebook.com/coder.OVI",
    websiteUrl = "https://ismamovi.dev/",
)
