package com.getauthepay.app.ui

/**
 * Minimal async result wrapper used by screen ViewModels.
 *
 * Chosen over a `Result`/`Either` type because screens need to distinguish
 * three states — loading, data, error — and must never render a stale value
 * as if it were fresh.
 */
sealed interface Async<out T> {
    data object Idle : Async<Nothing>
    data object Loading : Async<Nothing>
    data class Ok<out T>(val value: T) : Async<T>
    data class Error(val message: String, val cause: Throwable? = null) : Async<Nothing>

    companion object {
        fun <T> of(value: T): Async<T> = Ok(value)
    }
}

val <T> Async<T>.valueOrNull: T?
    get() = (this as? Async.Ok)?.value

val <T> Async<T>.isLoading: Boolean
    get() = this is Async.Loading

val <T> Async<T>.errorOrNull: String?
    get() = (this as? Async.Error)?.message
