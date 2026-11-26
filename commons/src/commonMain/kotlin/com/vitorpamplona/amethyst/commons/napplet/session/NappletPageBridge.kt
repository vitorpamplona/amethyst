/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.NappletActingRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.amethyst.commons.util.withString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.TimeSource

/**
 * The page side of a launch's bridge: what comes from the shell and what goes back to it, apart from
 * what the requests mean.
 *
 * - **In:** a message from the shell goes to [toTrusted] on the thread it arrived on, so NAP-INC keeps
 *   the order the page sent it in. While nobody is looking at the launch ([setAttended] false), a
 *   request that acts for the user ([NappletActingRequests]) is held instead, as Android's host does,
 *   and sent when the user is back; one held longer than [NappletHeldRequests.MAX_AGE_MS], or past the
 *   cap, is answered with a failure so the page's promise settles.
 * - **Out:** [deliver] queues a payload for the page; the queue goes to the shell in one script per
 *   burst ([deliveryScript]), in order: a subscription's stored events cost one trip to the renderer.
 *
 * Desktop and iOS run it in-process next to the trusted side; Android's sandbox process can run it on
 * its side of the IPC.
 */
class NappletPageBridge(
    private val scope: CoroutineScope,
    /** The trusted side: gets every message the page may send now. */
    private val toTrusted: (String) -> Unit,
    /** Runs a script in the shell (the top frame). */
    private val evaluateInShell: (String) -> Unit,
    private val clock: () -> Long = monotonicMillis(),
    private val maxHeldAgeMs: Long = NappletHeldRequests.MAX_AGE_MS,
) {
    private val lock = KmpLock()
    private val held = NappletHeldRequests<String>(clock, maxAgeMs = maxHeldAgeMs)
    private val expiries = ArrayList<Job>()
    private var attended = false
    private var closed = false

    private val outbox = ArrayDeque<String>()
    private var flushQueued = false

    /** Whether the user is looking at the launch: requests that act for them wait until they are. */
    fun setAttended(isAttended: Boolean) {
        val drained =
            lock.withLock {
                if (closed) return
                attended = isAttended
                if (!isAttended) return
                cancelExpiries()
                held.drain()
            }
        drained.fail.forEach { fail(it, NappletHeldRequests.EXPIRED) }
        drained.send.forEach(toTrusted)
    }

    /** One message from the shell (already through [NappletBridgeGate]). */
    fun receive(raw: String) {
        val refused: String? =
            lock.withLock {
                if (closed) return
                if (attended || !NappletActingRequests.actsForUser(typeOf(raw))) {
                    null
                } else {
                    val refused = held.hold(raw)
                    // Settled with an error if nobody comes back for it, so the page isn't left waiting.
                    if (refused == null) expiries += scope.launch { expireLater() }
                    refused ?: return
                }
            }
        if (refused != null) {
            fail(refused, NappletHeldRequests.TOO_MANY)
            return
        }
        toTrusted(raw)
    }

    private suspend fun expireLater() {
        delay(maxHeldAgeMs)
        val expired = lock.withLock { if (closed) emptyList() else held.expire() }
        expired.forEach { fail(it, NappletHeldRequests.EXPIRED) }
    }

    private fun cancelExpiries() {
        expiries.forEach { it.cancel() }
        expiries.clear()
    }

    /** Answers the page's [raw] request with a failure, in the reply shape the broker uses. */
    private fun fail(
        raw: String,
        reason: String,
    ) {
        val type = typeOf(raw) ?: "napplet"
        deliver(withReplyId(NappletProtocolJson.encodeResponse(type, NappletResponse.Failed(reason)), idOf(raw)))
    }

    /** Queues [payload] for the page. Dropped once the bridge is [close]d (late broker replies). */
    fun deliver(payload: String) {
        val schedule =
            lock.withLock {
                if (closed) return
                outbox.addLast(payload)
                if (flushQueued) {
                    false
                } else {
                    flushQueued = true
                    true
                }
            }
        if (schedule) scope.launch { flush() }
    }

    /**
     * Hands the page everything queued, in order, in one script. Held through the call, so two
     * flushes can't hand the page their batches out of order.
     */
    fun flush() {
        lock.withLock {
            flushQueued = false
            if (closed || outbox.isEmpty()) {
                outbox.clear()
                return
            }
            val batch = ArrayList<String>(outbox.size)
            while (outbox.isNotEmpty()) batch += outbox.removeFirst()
            evaluateInShell(deliveryScript(batch))
        }
    }

    /** The page went away: nothing more is delivered, and held requests are dropped. */
    fun close() {
        lock.withLock {
            closed = true
            outbox.clear()
            held.clear()
            cancelExpiries()
        }
    }

    companion object {
        /** The script that hands [payloads] to the shell's bridge object ([NappletWebContract.BRIDGE_NAME]). */
        fun deliveryScript(payloads: List<String>): String {
            val batch = payloads.joinToString(",") { JsonPrimitive(it).toString() }
            return "(function(m){var b=window.${NappletWebContract.BRIDGE_NAME};for(var i=0;i<m.length;i++)b.onmessage&&b.onmessage({data:m[i]});})([$batch]);"
        }

        fun typeOf(raw: String): String? = runCatching { NappletProtocolJson.readType(raw) }.getOrNull()

        fun idOf(raw: String): String? = runCatching { NappletProtocolJson.readId(raw) }.getOrNull()

        fun monotonicMillis(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}

/** [payload], a broker reply, answering the request [id] (as the shim matches replies), or as it is without one. */
fun withReplyId(
    payload: String,
    id: String?,
): String {
    if (id == null) return payload
    val result = parseJsonObjectOrNull(payload) ?: return payload
    return result.withString("id", id).toString()
}
