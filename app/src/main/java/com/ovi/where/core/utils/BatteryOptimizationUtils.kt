package com.ovi.where.core.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

object BatteryOptimizationUtils {
    
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE)
                as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }
    
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppSettings(context)
            }
        }
    }
    
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
    
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Returns true on OEM devices known to enforce extra battery-saver
     * policies that override the standard `isIgnoringBatteryOptimizations`
     * exemption. We don't try to deep-link into each vendor's auto-start
     * page — Android offers no stable API for that and the activity names
     * change between firmware versions. Instead, the UI surfaces a hint
     * pointing users at the generic app-info screen.
     *
     * Sources for the list: AOSP reports + dontkillmyapp.com — the
     * vendors with the most aggressive default kill behavior.
     */
    fun isAggressiveOemKnown(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer in setOf(
            "xiaomi", "redmi", "poco", // MIUI family
            "huawei", "honor",          // EMUI / HarmonyOS
            "oppo", "realme",           // ColorOS / RealmeUI
            "vivo", "iqoo",             // FunTouch / OriginOS
            "oneplus",                  // OxygenOS (less aggressive but still on the list)
            "asus",                     // ZenUI background-app limits
            "samsung"                   // OneUI puts apps to sleep aggressively
        )
    }
}
