package com.ovi.where.core.crash

import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe singleton that holds the currently active screen route name.
 * Updated by the navigation layer whenever the destination changes.
 * Read by the crash reporter to attach as a custom key.
 */
object ActiveScreenTracker {
    private val currentRoute = AtomicReference<String>("unknown")

    fun setActiveRoute(route: String) {
        currentRoute.set(route)
    }

    fun getActiveRoute(): String = currentRoute.get()
}
