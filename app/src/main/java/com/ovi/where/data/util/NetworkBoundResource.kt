package com.ovi.where.data.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A generic inline function implementing the Network Bound Resource pattern.
 *
 * Emission sequence:
 * 1. [Resource.Loading] with the current cached data (may be null if cache is empty)
 * 2. If [shouldFetch] returns true, fetches from network:
 *    - On success: saves the result via [saveFetchResult], then emits [Resource.Success] with fresh data from [query]
 *    - On failure: calls [onFetchFailed], then emits [Resource.Error] with the exception and existing cached data
 * 3. If [shouldFetch] returns false, emits [Resource.Success] with the cached data directly
 *
 * @param ResultType The type of data returned from the local cache (Room)
 * @param RequestType The type of data returned from the network fetch
 * @param query A function returning a [Flow] of the cached data from Room
 * @param fetch A suspend function that fetches fresh data from the network
 * @param saveFetchResult A suspend function that persists the network response to the local cache
 * @param shouldFetch A suspend function that determines whether a network fetch should be made based on the current cached data
 * @param onFetchFailed A callback invoked when the network fetch throws an exception
 */
inline fun <ResultType, RequestType> networkBoundResource(
    crossinline query: () -> Flow<ResultType>,
    crossinline fetch: suspend () -> RequestType,
    crossinline saveFetchResult: suspend (RequestType) -> Unit,
    crossinline shouldFetch: suspend (ResultType?) -> Boolean = { true },
    crossinline onFetchFailed: (Throwable) -> Unit = {}
): Flow<Resource<ResultType>> = flow {
    // Step 1: Emit Loading with current cached data
    val cachedData = query().first()
    emit(Resource.Loading(cachedData))

    if (shouldFetch(cachedData)) {
        try {
            // Step 2a: Fetch from network
            val fetchResult = fetch()
            // Save the fresh data to the local cache
            saveFetchResult(fetchResult)
            // Emit Success with the updated cached data from the query
            emitAll(query().map { Resource.Success(it) })
        } catch (throwable: Throwable) {
            // Step 2b: Network fetch failed
            onFetchFailed(throwable)
            // Emit Error with the exception and existing cached data (never discard cache)
            emitAll(query().map { Resource.Error(throwable, it) })
        }
    } else {
        // Step 3: Cache is fresh, no fetch needed
        emitAll(query().map { Resource.Success(it) })
    }
}
