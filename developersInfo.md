package com.om.diucampusschedule.ui.screens.settings

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.DeleteOutline
import coil.imageLoader
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.annotation.ExperimentalCoilApi
import com.om.diucampusschedule.R
import com.om.diucampusschedule.ui.navigation.Screen
import com.om.diucampusschedule.ui.theme.AccentGreen
import com.om.diucampusschedule.ui.theme.AccentOrange
import com.om.diucampusschedule.ui.theme.AccentTeal
import com.om.diucampusschedule.ui.utils.ScreenConfig
import com.om.diucampusschedule.ui.utils.ScreenConfig.Modifiers.mainAppScreen
import com.om.diucampusschedule.ui.utils.TopAppBarIconSize.topbarIconSize
import com.om.diucampusschedule.widget.NextClassWidgetProvider
import com.om.diucampusschedule.widget.TodaysClassesWidgetProvider
import com.om.diucampusschedule.widget.UpcomingClassesWidgetProvider
import com.om.diucampusschedule.widget.UpcomingExamsWidgetProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var showAboutUsSheet by remember { mutableStateOf(false) }
    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
    val appVersion = packageInfo?.versionName ?: "1.0.0"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(topbarIconSize)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                windowInsets = ScreenConfig.getTopAppBarWindowInsets(handleStatusBar = true)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .mainAppScreen(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            item {
                SettingsSectionTitle("Appearance")
                SettingsGroup(
                    items = listOf(
                        SettingsItemData(
                            iconRes = R.drawable.palette,
                            title = "Theme",
                            subtitle = "Customize app theme & colors",
                            onClick = { navController.navigate(Screen.Appearance.route) }
                        ),
                        SettingsItemData(
                            icon = Icons.Rounded.Widgets,
                            title = "Home Screen Widgets",
                            subtitle = "Add schedule widgets to your home screen",
                            onClick = { showWidgetPicker = true }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle("Notifications")
                SettingsGroup(
                    items = listOf(
                        SettingsItemData(
                            iconRes = R.drawable.notification_settings,
                            title = "Notification Settings",
                            subtitle = "Manage your class and exam reminders",
                            onClick = { navController.navigate(Screen.NotificationSettings.route) }
                        ),
                        SettingsItemData(
                            icon = Icons.Rounded.NotificationsPaused,
                            title = "Fix Notification Delays",
                            subtitle = "Optimize battery settings for timely alerts",
                            onClick = { navController.navigate(Screen.NotificationOptimization.route) }
                        ),
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle("App Data")
                SettingsGroup(
                    items = listOf(
                        SettingsItemData(
                            icon = Icons.Rounded.DeleteOutline,
                            title = "Clear Cache",
                            subtitle = "Free up storage space and fix issues",
                            onClick = {
                                context.cacheDir.deleteRecursively()
                                try {
                                    context.imageLoader.memoryCache?.clear()
                                    context.imageLoader.diskCache?.clear()
                                } catch (_: Exception) {
                                    // Ignore if coil image loader is not initialized
                                }
                                Toast.makeText(context, "App cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle("Support us")
                SettingsGroup(
                    items = listOf(
                        SettingsItemData(
                            iconRes = R.drawable.donate_heart,
                            title = "Donate Us",
                            subtitle = "Support our project",
                            onClick = { navController.navigate(Screen.Donate.route) }
                        ),
                        SettingsItemData(
                            iconRes = R.drawable.star_24,
                            title = "Rate This App",
                            subtitle = "Share your experience",
                            onClick = {
                                val packageName = context.packageName
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "market://details?id=$packageName".toUri()
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        "Play Store not found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ),
                        SettingsItemData(
                            icon = Icons.Rounded.Feedback,
                            title = "Send Feedback",
                            subtitle = "Help us improve",
                            onClick = { showFeedbackDialog = true }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle("About")
                SettingsGroup(
                    items = listOf(
                        SettingsItemData(
                            icon = Icons.Rounded.ThumbUp,
                            title = "Follow & Support",
                            subtitle = "Stay connected with us",
                            isExternal = true,
                            onClick = {
                                val url = "https://www.facebook.com/profile.php?id=61572247479723"
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                intent.setPackage("com.facebook.katana")
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    val fallbackIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(fallbackIntent)
                                }
                            }
                        ),
                        SettingsItemData(
                            iconRes = R.drawable.site,
                            title = "DIUCS Website",
                            subtitle = "Visit our web version of DIUCS",
                            isExternal = true,
                            onClick = {
                                val url = "https://diucampusschedule.app/"
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                context.startActivity(intent)
                            }
                        ),
                        SettingsItemData(
                            icon = Icons.Rounded.Groups,
                            title = "About Us",
                            subtitle = "Get to know the team",
                            onClick = { showAboutUsSheet = true }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(48.dp))
                GoogleStyleFooter(appVersion = appVersion)
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }

    if (showAboutUsSheet) {
        val developers = listOf(
            Developer(
                pictureRes = R.drawable.ovi,
                name = "Ismam Hasan Ovi",
                title = "SWE-41 | Android Developer",
                description = "Passionate Android Developer specialized in Jetpack Compose & clean architecture. Building modern, scalable mobile apps.",
                gmail = "ismamhasanovi@gmail.com",
                githubUrl = "https://github.com/oviii-001",
                linkedinUrl = "https://www.linkedin.com/in/ismamovi",
                facebookUrl = "https://www.facebook.com/coder.OVI",
                websiteUrl = "https://ismamovi.dev/"
            ),
            Developer(
                pictureRes = R.drawable.maruf,
                name = "Md Maruf Rayhan",
                title = "SWE-41 | Android Developer",
                description = "Passionate about building beautiful and functional Android applications with modern technologies.",
                gmail = "marufrayhan2002@gmail.com",
                githubUrl = "https://github.com/marufrayhan606",
                linkedinUrl = "https://www.linkedin.com/in/marufrayhan606/",
                facebookUrl = "https://www.facebook.com/marufrayhan606",
                websiteUrl = null
            )
        )
        AboutUsBottomSheet(
            onDismiss = { showAboutUsSheet = false },
            developers = developers,
            appVersion = appVersion
        )
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(
            context = context,
            onDismiss = { showWidgetPicker = false }
        )
    }

    if (showFeedbackDialog) {
        FeedbackChannelDialog(
            context = context,
            onDismiss = { showFeedbackDialog = false }
        )
    }
}

/**
 * Data class for widget items in the picker dialog.
 */
private data class WidgetItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val providerClass: Class<*>
)

/**
 * Material 3 dialog that lists all available widgets and lets users pin them to home screen.
 */
@Composable
private fun WidgetPickerDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val widgets = remember {
        listOf(
            WidgetItem(
                icon = Icons.Rounded.Schedule,
                title = "Next Class",
                subtitle = "Shows your upcoming next class",
                providerClass = NextClassWidgetProvider::class.java
            ),
            WidgetItem(
                icon = Icons.Rounded.CalendarMonth,
                title = "Today's Classes",
                subtitle = "Full list of today's classes",
                providerClass = TodaysClassesWidgetProvider::class.java
            ),
            WidgetItem(
                icon = Icons.Rounded.EventAvailable,
                title = "Upcoming Classes",
                subtitle = "View your upcoming class schedule",
                providerClass = UpcomingClassesWidgetProvider::class.java
            ),
            WidgetItem(
                icon = Icons.Rounded.EventAvailable,
                title = "Upcoming Exams",
                subtitle = "View your upcoming exam schedule",
                providerClass = UpcomingExamsWidgetProvider::class.java
            )
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Add Widget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose a widget to add to your home screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    widgets.forEach { widget ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    requestPinWidget(context, widget.providerClass)
                                    onDismiss()
                                },
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = widget.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = widget.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = widget.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Requests the system to pin a widget to the home screen using [AppWidgetManager.requestPinAppWidget].
 */
private fun requestPinWidget(context: Context, providerClass: Class<*>) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val widgetProvider = ComponentName(context, providerClass)

    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
    } else {
        Toast.makeText(
            context,
            "Your launcher doesn't support adding widgets this way. Please add it manually from your home screen.",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Clean M3 dialog with branded Messenger & Telegram channel options.
 */
@Composable
private fun FeedbackChannelDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Send Feedback",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose a feedback channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Messenger option
                Surface(
                    onClick = {
                        val url =
                            "https://m.me/ch/AbZ7MiSzbx1f5-rc/?send_source=cm%3Acopy_invite_link"
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        intent.setPackage("com.facebook.orca")
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.facebook_messenger),
                            contentDescription = "Messenger",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Messenger",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Feedback channel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Telegram option
                Surface(
                    onClick = {
                        val url = "https://t.me/c/3852425895/21"
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        intent.setPackage("org.telegram.messenger")
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.telegram),
                            contentDescription = "Telegram",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Telegram",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Feedback group",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 12.dp)
    )
}

data class SettingsItemData(
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val title: String,
    val subtitle: String? = null,
    val isExternal: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun SettingsGroup(
    items: List<SettingsItemData>
) {
    Column {
        items.forEachIndexed { index, item ->
            val shape = when {
                items.size == 1 -> RoundedCornerShape(20.dp)
                index == 0 -> RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 6.dp
                )

                index == items.lastIndex -> RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 6.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                )

                else -> RoundedCornerShape(6.dp)
            }
            SettingsItem(
                item = item,
                shape = shape
            )
        }
    }
}

@Composable
private fun SettingsItem(
    item: SettingsItemData,
    shape: Shape
) {
    Surface(
        onClick = item.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.5.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else if (item.iconRes != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = item.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.isExternal) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "External link",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GoogleStyleFooter(appVersion: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Text(
            text = "Version $appVersion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Made with ❤️ for DIU",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsBottomSheet(
    onDismiss: () -> Unit,
    developers: List<Developer>,
    appVersion: String
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Header with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "About Us",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // App Info Card - Redesigned with better spacing
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo - Larger and centered
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DIU Campus Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your Complete Campus Companion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Version badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = "Version $appVersion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Stats Cards Row - Improved design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    icon = Icons.Default.Download,
                    value = "5K+",
                    label = "Downloads",
                    containerColor = AccentGreen.copy(alpha = 0.1f),
                    contentColor = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    icon = Icons.Default.Star,
                    value = "4.8",
                    label = "Rating",
                    containerColor = AccentOrange.copy(alpha = 0.1f),
                    contentColor = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    icon = Icons.Default.People,
                    value = "5K+",
                    label = "Users",
                    containerColor = AccentTeal.copy(alpha = 0.1f),
                    contentColor = AccentTeal,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Developer Section Title - Improved
            Text(
                text = "DEVELOPERS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Developer Cards
            developers.forEach { developer ->
                DeveloperCard(developer = developer)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatsCard(
    icon: ImageVector,
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun DeveloperCard(developer: Developer) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Developer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture
            Image(
                painter = painterResource(developer.pictureRes),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = developer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = developer.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = developer.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Social Links Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GitHub
            SocialIconButton(
                iconRes = R.drawable.github_logo,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, developer.githubUrl.toUri()))
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            // LinkedIn
            SocialIconButton(
                iconRes = R.drawable.linkedin_logo,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, developer.linkedinUrl.toUri()))
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            // Facebook
            SocialIconButton(
                iconRes = R.drawable.facebook_logo,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, developer.facebookUrl.toUri()))
                }
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Website or Email
            if (developer.websiteUrl != null) {
                IconButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                developer.websiteUrl.toUri()
                            )
                        )
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Website",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:${developer.gmail}".toUri()
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SocialIconButton(iconRes: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                CircleShape
            )
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

data class Developer(
    val pictureRes: Int,
    val name: String,
    val title: String,
    val description: String,
    val gmail: String,
    val githubUrl: String,
    val linkedinUrl: String,
    val facebookUrl: String,
    val websiteUrl: String? = null
)