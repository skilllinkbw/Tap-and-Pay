package com.getauthepay.app.core.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Idempotency guarantees for payment submission.
 *
 * Directive section 19: if a merchant presses "Pay" several times the system
 * must not create several payments. The store is the first line of defence;
 * the processor is the authoritative one.
 */
class IdempotencyStoreTest {

    @Test
    fun `first begin for a key is accepted`() {
        val store = IdempotencyStore<String>()
        assertTrue(store.begin("k1"))
    }

    @Test
    fun `beginning a completed key reports that it is not new`() {
        val store = IdempotencyStore<String>()
        store.begin("k1")
        store.complete("k1", "result")
        assertFalse(store.begin("k1"))
    }

    @Test
    fun `cached returns null until the slot completes`() {
        val store = IdempotencyStore<String>()
        store.begin("k1")
        assertNull(store.cached("k1"))
    }

    @Test
    fun `cached returns the completed result`() {
        val store = IdempotencyStore<String>()
        store.begin("k1")
        store.complete("k1", "done")
        assertEquals("done", store.cached("k1"))
    }

    @Test
    fun `cached for an unknown key is null`() {
        assertNull(IdempotencyStore<String>().cached("nope"))
    }

    @Test
    fun `discard removes the slot so the key can be retried`() {
        val store = IdempotencyStore<String>()
        store.begin("k1")
        store.complete("k1", "done")
        store.discard("k1")
        assertNull(store.cached("k1"))
        assertTrue(store.begin("k1"))
    }

    @Test
    fun `clear empties every slot`() {
        val store = IdempotencyStore<String>()
        store.begin("a")
        store.begin("b")
        assertEquals(2, store.size())
        store.clear()
        assertEquals(0, store.size())
    }

    @Test
    fun `keys are isolated from one another`() {
        val store = IdempotencyStore<String>()
        store.begin("a")
        store.complete("a", "A")
        store.begin("b")
        assertEquals("A", store.cached("a"))
        assertNull(store.cached("b"))
    }

    @Test
    fun `completing an unknown key is a safe no-op`() {
        val store = IdempotencyStore<String>()
        store.complete("ghost", "value")
        assertNull(store.cached("ghost"))
    }

    @Test
    fun `repeated begin on the same in-flight key is rejected (single-flight)`() {
        // Regression: the previous implementation returned true for an
        // in-flight key, allowing two concurrent submissions of the same
        // payment. Single-flight means exactly ONE caller may pass begin().
        val store = IdempotencyStore<String>()
        assertTrue(store.begin("k"))
        assertFalse("in-flight duplicate must be rejected", store.begin("k"))
        assertEquals(1, store.size())
    }

    @Test
    fun `begin is atomic under concurrent access`() {
        val store = IdempotencyStore<String>()
        val winners = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = (1..16).map {
            Thread {
                if (store.begin("race-key")) winners.incrementAndGet()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals("exactly one thread may win the single-flight gate", 1, winners.get())
    }
}
