package com.macrotracker.data.hermes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewestLastTest {

    /** A request that, like a blocking HTTP call, runs to its answer even when cancelled. */
    private suspend fun wire(sent: MutableList<String>, id: String, hold: CompletableDeferred<Unit>? = null): String =
        withContext(NonCancellable) {
            sent += id
            hold?.await()
            id
        }

    @Test
    fun `three quick picks reach the server in order, the middle one never goes, only the last answers`() = runBlocking {
        val queue = NewestLast()
        val sent = mutableListOf<String>()
        val answered = mutableListOf<String>()
        val lowIsSlow = CompletableDeferred<Unit>()

        queue.submit(this, { wire(sent, "low", lowIsSlow) }) { answered += it.getOrThrow() }
        yield() // low is on the wire
        queue.submit(this, { wire(sent, "med") }) { answered += it.getOrThrow() }
        yield()
        queue.submit(this, { wire(sent, "high") }) { answered += it.getOrThrow() }
        yield()
        // high waits behind low; it must not overtake a request that has not heard back
        assertEquals(listOf("low"), sent)

        lowIsSlow.complete(Unit)
        queue.join()
        assertEquals(listOf("low", "high"), sent)
        assertEquals(listOf("high"), answered)
    }

    @Test
    fun `a failure is handed on, and the next request still goes`() = runBlocking {
        val queue = NewestLast()
        var failure: Throwable? = null
        queue.submit<String>(this, { error("Hermes said no") }) { failure = it.exceptionOrNull() }
        queue.join()
        assertTrue(failure is IllegalStateException)

        val answered = mutableListOf<String>()
        queue.submit(this, { "ok" }) { answered += it.getOrThrow() }
        queue.join()
        assertEquals(listOf("ok"), answered)
    }
}
