package com.ovi.where.core.common

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
    
    fun getOrThrow(): T {
        return when (this) {
            is Success -> data!!
            is Error -> throw IllegalStateException(message ?: "Resource is in error state")
            is Loading -> throw IllegalStateException("Resource is in loading state")
        }
    }
    
    inline fun <R> map(transform: (T) -> R): Resource<R> {
        return when (this) {
            is Success -> Success(transform(data!!))
            is Error -> Error(message ?: "Unknown error")
            is Loading -> Loading(data?.let(transform))
        }
    }
    
    inline fun onSuccess(action: (T) -> Unit): Resource<T> {
        if (this is Success) action(data!!)
        return this
    }
    
    inline fun onError(action: (String) -> Unit): Resource<T> {
        if (this is Error) action(message ?: "Unknown error")
        return this
    }
    
    inline fun onLoading(action: () -> Unit): Resource<T> {
        if (this is Loading) action()
        return this
    }
}
