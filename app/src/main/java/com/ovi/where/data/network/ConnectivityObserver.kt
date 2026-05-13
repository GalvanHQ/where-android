package com.ovi.where.data.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes network connectivity state and exposes it as a reactive StateFlow.
 */
interface ConnectivityObserver {
    val isConnected: StateFlow<Boolean>
}
