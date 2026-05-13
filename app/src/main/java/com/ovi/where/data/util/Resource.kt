package com.ovi.where.data.util

/**
 * A sealed class representing the state of a data-fetching operation
 * used by [networkBoundResource]. Carries optional cached data alongside
 * the loading/success/error status.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val throwable: Throwable, val data: T? = null) : Resource<T>()
    data class Loading<T>(val data: T? = null) : Resource<T>()
}
