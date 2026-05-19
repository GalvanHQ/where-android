package com.ovi.where.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.core.constants.AppConstants.MILLIS_PER_MINUTE
import com.ovi.where.data.local.prefs.UserPreferences
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service for continuous location tracking during an active sharing session.
 *
 * Key behaviors:
 * - Uses adaptive location updates (high-accuracy when moving, balanced when idle)
 * - Automatically expires based on the selected duration
 * - Updates the notification with remaining time every minute
 * - Sends a "sharing ended" notification on expiration
 * - Gracefully stops and cleans up Firestore state on expiry
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var locationManager: LocationManager
    
    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var userPreferences: UserPreferences
    
    private var currentGroupId: String? = null
    private var expiresAt: Long? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentGroupId = intent.getStringExtra(EXTRA_GROUP_ID)
                val duration = intent.getLongExtra(EXTRA_DURATION_MINUTES, -1L)
                expiresAt = if (duration > 0) {
                    System.currentTimeMillis() + duration * MILLIS_PER_MINUTE
                } else {
                    null // Continuous sharing (manual stop)
                }
                // Persist session state for restart recovery
                serviceScope.launch {
                    currentGroupId?.let { gid ->
                        userPreferences.saveSharingSession(gid, expiresAt)
                    }
                }
                startForegroundService()
            }
            ACTION_STOP -> {
                serviceScope.launch { userPreferences.clearSharingSession() }
                stopSharing()
                stopSelf()
            }
            else -> {
                // Null intent: system restarted the service after a kill.
                // Recover session from DataStore.
                Timber.w("Service restarted with null intent — recovering from DataStore")
                serviceScope.launch {
                    val savedTargetId = userPreferences.sharingTargetId.first()
                    val savedExpiry = userPreferences.sharingExpiresAt.first()

                    if (savedTargetId.isNullOrEmpty()) {
                        Timber.i("No persisted session — stopping service")
                        stopSelf()
                        return@launch
                    }

                    // Check if session already expired while service was dead
                    if (savedExpiry != null && System.currentTimeMillis() > savedExpiry) {
                        Timber.i("Persisted session already expired — cleaning up")
                        locationRepository.stopLocationSharing(savedTargetId)
                        userPreferences.clearSharingSession()
                        showExpirationNotification()
                        stopSelf()
                        return@launch
                    }

                    // Restore active session
                    currentGroupId = savedTargetId
                    expiresAt = savedExpiry
                    Timber.i("Recovered session: group=$savedTargetId, expires=$savedExpiry")
                    startForegroundService()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = buildNotification(formatRemainingTime())
        startForeground(AppConstants.NOTIFICATION_ID, notification)
        startLocationTracking()
        startNotificationUpdater()
    }

    /**
     * Uses adaptive location updates for battery optimization.
     * When the user is stationary, updates drop to 30s intervals.
     * When moving, updates are at 5s intervals with high accuracy.
     */
    @Suppress("MissingPermission")
    private fun startLocationTracking() {
        val groupId = currentGroupId ?: return
        
        serviceScope.launch {
            locationManager.getAdaptiveLocationUpdates()
                .catch { e ->
                    Timber.e(e, "Location updates failed")
                }
                .collect { location ->
                    // Check expiration
                    val currentExpiry = expiresAt
                    if (currentExpiry != null && System.currentTimeMillis() > currentExpiry) {
                        Timber.i("Location tracking session expired")
                        onSessionExpired(groupId)
                        return@collect
                    }

                    locationRepository.updateLocation(
                        groupId = groupId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        speed = location.speed,
                        bearing = location.bearing
                    )
                }
        }
    }

    /**
     * Updates the notification every 60 seconds with remaining time.
     * Gives the user visibility into how long sharing will continue.
     */
    private fun startNotificationUpdater() {
        if (expiresAt == null) return // Continuous mode — no countdown needed

        serviceScope.launch {
            while (true) {
                delay(60_000L)
                val remaining = formatRemainingTime()
                if (remaining != null) {
                    val notification = buildNotification(remaining)
                    notificationManager?.notify(AppConstants.NOTIFICATION_ID, notification)
                }
            }
        }
    }

    /**
     * Called when the sharing session expires naturally.
     * Stops Firestore sharing, shows "ended" notification, and stops the service.
     */
    private fun onSessionExpired(groupId: String) {
        serviceScope.launch {
            locationRepository.stopLocationSharing(groupId)
            userPreferences.clearSharingSession()
        }
        showExpirationNotification()
        stopSelf()
    }

    private fun stopSharing() {
        val groupId = currentGroupId ?: return
        serviceScope.launch {
            locationRepository.stopLocationSharing(groupId)
            userPreferences.clearSharingSession()
        }
    }

    private fun buildNotification(remainingText: String?): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (remainingText != null) {
            getString(R.string.notification_sharing_body) + " • $remainingText remaining"
        } else {
            getString(R.string.notification_sharing_body) + " • Continuous"
        }

        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_sharing_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Stop",
                stopIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Shows a notification when sharing expires naturally, so the user knows
     * their location is no longer visible to the group.
     */
    private fun showExpirationNotification() {
        val notification = NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location sharing ended")
            .setContentText("Your live location is no longer visible to the group")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(EXPIRATION_NOTIFICATION_ID, notification)
    }

    private fun formatRemainingTime(): String? {
        val expiry = expiresAt ?: return null
        val remainingMs = expiry - System.currentTimeMillis()
        if (remainingMs <= 0) return null

        val minutes = remainingMs / MILLIS_PER_MINUTE
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ID,
                AppConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_GROUP_ID = "EXTRA_GROUP_ID"
        const val EXTRA_DURATION_MINUTES = "EXTRA_DURATION_MINUTES"
        private const val EXPIRATION_NOTIFICATION_ID = 1002
        
        fun createStartIntent(context: Context, groupId: String, durationMinutes: Long): Intent {
            return Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
            }
        }
        
        fun createStopIntent(context: Context): Intent {
            return Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
