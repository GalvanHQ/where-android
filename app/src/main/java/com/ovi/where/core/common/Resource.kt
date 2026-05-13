package com.ovi.where.core.common

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)

    val isSuccess: Boolean get() = this is Success
    val isLoading: Boolean get() = this is Loading

    inline fun <R> map(transform: (T) -> R): Resource<R> {
        return when (this) {
            is Success -> Success(transform(data!!))
            is Error -> Error(message ?: "Unknown error")
            is Loading -> Loading(data?.let(transform))
        }
    }
}
