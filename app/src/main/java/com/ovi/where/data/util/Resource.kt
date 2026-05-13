package com.ovi.where.data.util

/**
 * A sealed class representing the state of a data-fetching operation
 * used by [networkBoundResource]. Carries optional cached data alongside
 * the loading/success/error status.
 *
 * Note: This is intentionally separate from [com.ovi.where.core.common.Resource]
 * which uses String error messages. This class carries the full [Throwable] for
 * richer error handling in the NetworkBoundResource pattern.
 *
 * Domain-layer interfaces import this via a typealias to avoid coupling the
 * domain API surface to the data package directly.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val throwable: Throwable, val data: T? = null) : Resource<T>()
    data class Loading<T>(val data: T? = null) : Resource<T>()
}
