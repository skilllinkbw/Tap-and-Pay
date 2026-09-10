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
        var inFlight: Boolean = true,
        var result: T? = null,
    )

    private val slots = ConcurrentHashMap<String, Slot<T>>()

    /** Returns true if this is a new key, false if the key is already known. */
    fun begin(key: String): Boolean {
        val slot = slots.computeIfAbsent(key) { Slot(key) }
        return if (slot.inFlight) {
            slot.inFlight = true
            true
        } else {
            // Already completed — return the cached result; the engine short-circuits.
            false
        }
    }

    /** Records the completed result and marks the slot as finished. */
    fun complete(key: String, result: T) {
        val slot = slots[key]
        if (slot != null) {
            slot.result = result
            slot.inFlight = false
        }
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