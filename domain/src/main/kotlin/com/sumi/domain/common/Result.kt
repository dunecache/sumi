package com.sumi.domain.common

/** Lightweight result type for one-shot domain operations. */
sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>
    data class Failure(val throwable: Throwable) : Result<Nothing>
}

inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (this is Result.Success) action(value)
    return this
}

inline fun <T> Result<T>.onFailure(action: (throwable: Throwable) -> Unit): Result<T> {
    if (this is Result.Failure) action(throwable)
    return this
}
