package com.ovi.where.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

object IntentUtils {
    
    fun createShareIntent(title: String, content: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
    }
    
    fun createShareIntent(context: Context, title: String, content: String): Intent {
        return Intent.createChooser(createShareIntent(title, content), title)
    }
    
    fun createMapIntent(latitude: Double, longitude: Double, label: String? = null): Intent {
        val uri = if (label != null) {
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
        } else {
            Uri.parse("geo:$latitude,$longitude")
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }
    
    fun createNavigationIntent(latitude: Double, longitude: Double): Intent {
        val uri = Uri.parse("google.navigation:q=$latitude,$longitude")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }
    
    fun createDialIntent(phoneNumber: String): Intent {
        val uri = Uri.parse("tel:$phoneNumber")
        return Intent(Intent.ACTION_DIAL, uri)
    }
    
    fun createEmailIntent(to: String, subject: String? = null, body: String? = null): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$to")
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
    }
    
    fun createWebIntent(url: String): Intent {
        val uri = Uri.parse(url)
        return Intent(Intent.ACTION_VIEW, uri)
    }
}
