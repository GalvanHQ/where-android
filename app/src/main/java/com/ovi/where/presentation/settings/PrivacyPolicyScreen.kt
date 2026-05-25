package com.ovi.where.presentation.settings

import androidx.compose.runtime.Composable

private const val LAST_UPDATED = "May 25, 2026"
private const val SUPPORT_EMAIL = "ismamhasanovi@gmail.com"

private val PRIVACY_SECTIONS = listOf(
    LegalSection(
        heading = "What this policy covers",
        body = "This Privacy Policy explains what information Where collects, " +
            "how we use it, and the choices you have. It applies to the Where " +
            "mobile app and the services it talks to (Firebase, Google Maps).",
    ),
    LegalSection(
        heading = "Information you give us",
        body = "When you sign up, we collect your display name, username, " +
            "email address, and an optional profile photo. When you create " +
            "groups or send messages, we store that content so the people " +
            "you sent it to can read it.",
    ),
    LegalSection(
        heading = "Location data",
        body = "Where is a location-sharing app, so location is core to the " +
            "product. We collect your device's location only while you are " +
            "actively sharing with friends or groups. When you stop sharing, " +
            "we stop reading your location. The most recent shared coordinate " +
            "is kept on the server briefly so friends who open the app right " +
            "after you stop see your last position before it disappears.",
    ),
    LegalSection(
        heading = "Information your device sends automatically",
        body = "We collect basic technical information needed to deliver " +
            "notifications and keep the app working: a Firebase Cloud " +
            "Messaging token, an anonymous app-install id, and crash reports " +
            "via Firebase Crashlytics. We do not sell or share this data.",
    ),
    LegalSection(
        heading = "How we use your data",
        body = "We use your data to operate the app: showing your location " +
            "to people you choose, delivering chat messages, sending push " +
            "notifications about friend requests and meetups, and helping " +
            "us diagnose crashes. We do not use your data for advertising.",
    ),
    LegalSection(
        heading = "Who can see your information",
        body = "Your location is visible only to friends and group members " +
            "you've chosen to share with — and only while you're actively " +
            "sharing. Your messages are visible to the conversation members " +
            "you sent them to. Your profile (name, username, photo) is " +
            "visible to your friends and to people who search for your " +
            "username, depending on your privacy settings.",
    ),
    LegalSection(
        heading = "Data retention",
        body = "Messages and shared media stay in the conversation until " +
            "you or another participant deletes them, or until you delete " +
            "your account. In-app notifications older than 30 days are " +
            "automatically pruned. When you delete your account, we remove " +
            "your profile, friendships, and personal data; messages you " +
            "sent to others may remain in their copies of the conversation.",
    ),
    LegalSection(
        heading = "Your choices",
        body = "You control your privacy from Settings:" +
            "\n• Privacy → choose who sees your location and who can find " +
            "your profile." +
            "\n• Permissions → revoke location, notifications, or other " +
            "device permissions any time." +
            "\n• Notification Preferences → toggle which categories you " +
            "want to be notified about." +
            "\n• Blocked → manage who can contact you." +
            "\n• Delete Account → permanently remove your data.",
    ),
    LegalSection(
        heading = "Children",
        body = "Where is not directed to children under 13. We do not " +
            "knowingly collect data from children under 13. If you believe " +
            "a child has signed up, contact $SUPPORT_EMAIL and we will " +
            "remove the account.",
    ),
    LegalSection(
        heading = "Third-party services",
        body = "Where uses Firebase (Authentication, Firestore, Cloud " +
            "Functions, Crashlytics, Cloud Messaging) by Google, and Google " +
            "Maps to render the map. Their handling of data is governed by " +
            "Google's privacy policies.",
    ),
    LegalSection(
        heading = "Changes to this policy",
        body = "We may update this policy as the app evolves. The latest " +
            "version is always available here, with the updated date at the " +
            "top.",
    ),
    LegalSection(
        heading = "Contact",
        body = "Questions about this policy or your data? Reach out to " +
            "$SUPPORT_EMAIL.",
    ),
)

@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
) {
    LegalDocScreen(
        title = "Privacy Policy",
        sections = PRIVACY_SECTIONS,
        lastUpdated = LAST_UPDATED,
        onNavigateBack = onNavigateBack,
    )
}
