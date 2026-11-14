package com.macrotracker.data.hermes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Requests that each replace the one before, sent one at a time in the order asked. A
 * request waits until the one ahead of it has heard back (a blocking call that was
 * cancelled still runs to its answer), and one that is replaced while it waits is never
 * sent. So the newest is always the last the server hears and the only one that answers,
 * however fast they come. Hermes' model switch uses it.
 */
class NewestLast {
    private val lock = Mutex()
    private var job: Job? = null

    /** Sends [request] after whatever is on the wire; [then] gets its answer unless a newer one replaced it. */
    fun <T> submit(scope: CoroutineScope, request: suspend () -> T, then: (Result<T>) -> Unit) {
        job?.cancel()
        job = scope.launch {
            val result = try {
                val answer = lock.withLock { request() }
                // replaced while it was out: the answer is the older one's, not ours to show
                ensureActive()
                Result.success(answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            then(result)
        }
    }

    /** Waits for the newest request to finish. */
    suspend fun join() {
        job?.join()
    }
}
