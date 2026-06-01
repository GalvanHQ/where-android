package com.ovi.where.core.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.ovi.where.DeepLinkManager
import timber.log.Timber

/**
 * Single entry point for opening any link surfaced by the app.
 *
 * Routes by [LinkParser.Kind]:
 *   • [LinkParser.Kind.INTERNAL] (`where://...`) → [DeepLinkManager.pending],
 *     which AppNavGraph picks up next frame. Stays inside the app.
 *   • [LinkParser.Kind.EXTERNAL] (http / https / bare domains) → Custom Tab
 *     when the user has Chrome / a CCT-capable browser installed,
 *     otherwise the system's default `ACTION_VIEW` chooser.
 *   • [LinkParser.Kind.EMAIL] / [LinkParser.Kind.PHONE] → ACTION_VIEW with
 *     the appropriate URI.
 *
 * Why Custom Tabs for external? Custom Tabs render with the app's primary
 * color, share auth state with the user's browser, and come back to us
 * instantly when the user hits "back". A raw `Intent.ACTION_VIEW` typically
 * cold-starts a full browser, which is jarring after tapping a chat link.
 *
 * Failure handling: every path falls through to a debug log instead of
 * crashing. There's no scenario where opening a link should kill the app.
 */
object LinkRouter {

    /**
     * Dispatch a parsed match. The caller already knows the kind so we
     * don't re-detect.
     */
    fun open(context: Context, match: LinkParser.Match) {
        when (match.kind) {
            LinkParser.Kind.INTERNAL -> openInternal(match.targetUrl)
            LinkParser.Kind.EXTERNAL -> openExternal(context, match.targetUrl)
            LinkParser.Kind.EMAIL -> openMailto(context, match.targetUrl)
            LinkParser.Kind.PHONE -> openTel(context, match.targetUrl)
        }
    }

    /**
     * Best-effort open of an arbitrary string. Useful when the caller has
     * a raw URL (e.g. from `LinkPreviewCard`'s prefab metadata) without
     * having gone through [LinkParser].
     */
    fun openRaw(context: Context, url: String) {
        if (url.isBlank()) return
        if (url.startsWith("where://", ignoreCase = true)) {
            openInternal(url)
            return
        }
        if (url.startsWith("mailto:", ignoreCase = true)) {
            openMailto(context, url)
            return
        }
        if (url.startsWith("tel:", ignoreCase = true)) {
            openTel(context, url)
            return
        }
        val normalized =
            if (url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
            ) url
            else "https://$url"
        openExternal(context, normalized)
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun openInternal(uri: String) {
        // Convert `where://chat/abc` → `chat/abc` for the in-app router.
        // Reuses the existing parsing path so we behave identically to
        // notification-tap deep-links.
        val parsed = DeepLinkManager.parseWhereUri(uri.toUri())
        if (parsed != null) {
            DeepLinkManager.pending = parsed
        } else {
            Timber.w("Unrecognized internal URI: %s", uri)
        }
    }

    private fun openExternal(context: Context, url: String) {
        val uri = runCatching { url.toUri() }.getOrNull() ?: run {
            Timber.w("Could not parse external URL: %s", url)
            return
        }
        // Try Custom Tabs first; if the device has no CCT-capable browser,
        // fall through to a plain VIEW intent.
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
                .also {
                    it.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                .launchUrl(context, uri)
        }.onFailure {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.onFailure { fallbackErr ->
                Timber.w(fallbackErr, "Could not open external URL: %s", url)
            }
        }
    }

    private fun openMailto(context: Context, mailto: String) {
        try {
            val uri = if (mailto.startsWith("mailto:")) mailto.toUri() else "mailto:$mailto".toUri()
            val intent = Intent(Intent.ACTION_SENDTO, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No email client installed")
        }
    }

    private fun openTel(context: Context, tel: String) {
        try {
            val uri = if (tel.startsWith("tel:")) tel.toUri() else "tel:$tel".toUri()
            // ACTION_DIAL doesn't require CALL_PHONE permission and just
            // opens the dialer with the number pre-filled — much safer for
            // a chat link tap than ACTION_CALL.
            val intent = Intent(Intent.ACTION_DIAL, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No dialer available")
        }
    }
}
