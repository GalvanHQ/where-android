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
import com.google.firebase.firestore.FirebaseFirestore
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants
import com.ovi.where.data.location.LocationManager
import com.ovi.where.domain.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var locationManager: LocationManager
    
    @Inject
    lateinit var locationRepository: LocationRepository
    
    private var currentGroupId: String? = null
    private var expiresAt: Long? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentGroupId = intent.getStringExtra(EXTRA_GROUP_ID)
                val duration = intent.getLongExtra(EXTRA_DURATION_MINUTES, -1L)
                if (duration != -1L) {
                    expiresAt = System.currentTimeMillis() + duration * 60_000
                }
                startForegroundService()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        startForeground(AppConstants.NOTIFICATION_ID, notification)
        startLocationTracking()
    }

    @Suppress("MissingPermission")
    private fun startLocationTracking() {
        val groupId = currentGroupId ?: return
        
        serviceScope.launch {
            locationManager.getLocationUpdates()
                .catch { e ->
                    Timber.e(e, "Location updates failed")
                }
                .collect { location ->
                    val currentExpiry = expiresAt
                    if (currentExpiry != null && System.currentTimeMillis() > currentExpiry) {
                        Timber.d("Location tracking session expired")
                        serviceScope.launch {
                            locationRepository.stopLocationSharing(groupId)
                        }
                        stopSelf()
                        return@collect
                    }

                    Timber.d("Location update: lat=${location.latitude}, lng=${location.longitude}")
                    
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

    private fun buildNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_sharing_title))
            .setContentText(getString(R.string.notification_sharing_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
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
