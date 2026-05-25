package com.ovi.where.presentation.settings

import androidx.compose.runtime.Composable

private const val LAST_UPDATED = "May 25, 2026"
private const val SUPPORT_EMAIL = "ismamhasanovi@gmail.com"

private val TERMS_SECTIONS = listOf(
    LegalSection(
        heading = "Welcome to Where",
        body = "These Terms of Service govern your use of the Where mobile " +
            "application. By creating an account or using the app, you agree " +
            "to these terms. If you do not agree, please stop using the app.",
    ),
    LegalSection(
        heading = "Eligibility",
        body = "You must be at least 13 years old to use Where. If you are " +
            "under 18, you confirm that you have your parent or guardian's " +
            "permission to use the app.",
    ),
    LegalSection(
        heading = "Your account",
        body = "You are responsible for keeping your sign-in credentials secure. " +
            "You must provide accurate information when creating your profile " +
            "and keep it up to date. You are responsible for all activity that " +
            "happens on your account.",
    ),
    LegalSection(
        heading = "Acceptable use",
        body = "You agree not to use Where to harass, threaten, impersonate, " +
            "or stalk anyone. You will not share another person's location " +
            "without their consent, post unlawful content, or attempt to " +
            "interfere with the app's normal operation.",
    ),
    LegalSection(
        heading = "Location sharing",
        body = "Where lets you share your real-time location with friends and " +
            "groups you choose. You control when sharing starts and stops. " +
            "You can revoke sharing at any time from the map or chat screens. " +
            "Friends you share with may see your location until you stop.",
    ),
    LegalSection(
        heading = "Content you share",
        body = "Messages, locations, photos, and other content you send through " +
            "Where remain yours. You grant Where a limited license to store " +
            "and deliver that content to the recipients you choose, only for " +
            "the purpose of running the service.",
    ),
    LegalSection(
        heading = "Service availability",
        body = "Where is offered as-is. We work hard to keep the service " +
            "running, but we do not guarantee uninterrupted availability. We " +
            "may update, change, or remove features at any time.",
    ),
    LegalSection(
        heading = "Termination",
        body = "You can delete your account at any time from Settings. We may " +
            "suspend or terminate your access if you violate these terms or " +
            "use the app in a way that harms other users.",
    ),
    LegalSection(
        heading = "Limitation of liability",
        body = "Where is provided to you free of charge. To the extent allowed " +
            "by law, we are not liable for indirect or consequential damages " +
            "arising from your use of the app.",
    ),
    LegalSection(
        heading = "Changes to these terms",
        body = "We may update these terms from time to time. The latest version " +
            "is always available in this screen. Continued use of the app " +
            "after an update means you accept the new terms.",
    ),
    LegalSection(
        heading = "Contact",
        body = "Questions about these terms? Reach out to $SUPPORT_EMAIL.",
    ),
)

@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit,
) {
    LegalDocScreen(
        title = "Terms of Service",
        sections = TERMS_SECTIONS,
        lastUpdated = LAST_UPDATED,
        onNavigateBack = onNavigateBack,
    )
}
