package com.ovi.where.core.notification

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for grabbing notification-system singletons from Compose
 * screens without forcing every screen ViewModel to take them as a
 * constructor dependency.
 *
 * Why an entry point? The trackers + helper are pure runtime hooks the
 * UI uses to coordinate suppression and cancellation. Threading them
 * through every chat or map ViewModel constructor would break a lot of
 * existing tests for no gain — the tests don't care about notification
 * suppression. Resolving via this entry point keeps the cost contained
 * to the screen file that needs it.
 *
 * Usage from a Compose screen:
 *
 *     val context = LocalContext.current
 *     DisposableEffect(conversationId) {
 *         val tracker = context.activeConversationTracker()
 *         tracker.setActive(conversationId)
 *         onDispose { tracker.setActive(null) }
 *     }
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationTrackerEntryPoint {
    fun activeConversationTracker(): ActiveConversationTracker
    fun activeMapTracker(): ActiveMapTracker
    fun notificationHelper(): NotificationHelper
}

/** Convenience accessor for [ActiveConversationTracker]. */
fun Context.activeConversationTracker(): ActiveConversationTracker =
    NotificationTrackerEntryPointHolder.entryPoint(this).activeConversationTracker()

/** Convenience accessor for [ActiveMapTracker]. */
fun Context.activeMapTracker(): ActiveMapTracker =
    NotificationTrackerEntryPointHolder.entryPoint(this).activeMapTracker()

/**
 * Internal cache + entry-point resolver. We expose a small object instead
 * of inlining the [EntryPointAccessors] call at every site so the
 * Compose-side imports stay short and the resolver lives in one place.
 */
internal object NotificationTrackerEntryPointHolder {
    fun entryPoint(context: Context): NotificationTrackerEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationTrackerEntryPoint::class.java
        )

    fun helper(context: Context): NotificationHelper = entryPoint(context).notificationHelper()
}
