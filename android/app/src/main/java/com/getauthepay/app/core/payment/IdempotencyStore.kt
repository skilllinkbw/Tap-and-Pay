package com.getauthepay.app.core.payment

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory idempotency store. The keying contract:
 *
 *   - When the merchant presses "Pay" twice quickly, the engine MUST
 *     return the same PaymentResult for both invocations rather than
 *     create two distinct transactions.
 *   - The store enforces a single-flight guarantee per idempotency key.
 *   - On merchant reset or app restart, the store is cleared.
 *
 * The server-side processor is the authoritative deduplicator; this store
 * exists so the UI never observes a partial double-charge even before
 * the processor round-trip completes.
 */
class IdempotencyStore<T> {

    private data class Slot<T>(
        val key: String,
        var result: T? = null,
    )

    private val slots = ConcurrentHashMap<String, Slot<T>>()

    /**
     * Single-flight gate. Returns true ONLY for the first caller of a key.
     * Whether the first attempt is still in flight or already completed,
     * every subsequent caller gets false and must replay the result via
     * [cached] (or be rejected by the caller). Atomic via
     * [ConcurrentHashMap.putIfAbsent], so concurrent double-taps on the same
     * key can never both pass — the previous implementation returned true
     * for in-flight keys, which broke the single-flight guarantee.
     */
    fun begin(key: String): Boolean = slots.putIfAbsent(key, Slot(key)) == null

    /** Records the completed result. */
    fun complete(key: String, result: T) {
        slots[key]?.result = result
    }

    /** Returns the cached result for [key], or null if not present. */
    fun cached(key: String): T? = slots[key]?.result

    /** Discards a slot (e.g. on merchant "Try Again"). */
    fun discard(key: String) {
        slots.remove(key)
    }

    /** Clears all stored keys. */
    fun clear() {
        slots.clear()
    }

    /** Diagnostic: number of distinct keys held. */
    fun size(): Int = slots.size
}