package com.ledgerengine.network

/** Explicit success/failure channel so transport errors never leak as uncaught exceptions into the sync layer. */
sealed interface NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>

    /** Server reached and responded, but rejected the request (4xx/5xx with a parsed body). */
    data class ApiError(val httpStatus: Int, val message: String?) : NetworkResult<Nothing>

    /** Never reached the server: no connectivity, timeout, DNS failure, TLS error, etc. */
    data class ConnectivityError(val cause: Throwable) : NetworkResult<Nothing>
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(value))
    is NetworkResult.ApiError -> this
    is NetworkResult.ConnectivityError -> this
}
