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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncOrFetch
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression guard for the primal.net / purplepag.es negentropy stall
 * (`quartz/plans/2026-07-27-negentropy-notice-rejection-stall.md`).
 *
 * These relays advertise NIP-77 in NIP-11 but refuse it at runtime, answering
 * `NEG-OPEN` with a connection-level `NOTICE` (which carries no subId) instead of
 * a subId-addressed `NEG-ERR`:
 *   - strfry, negentropy off: `"ERROR: bad msg: negentropy disabled"`
 *   - purplepag.es:           `"failed to parse envelope: unknown envelope label"`
 *
 * Before the fix the reconcile driver only reacted to `NEG-MSG`/`NEG-ERR` for its
 * exact subId, so the `NOTICE` was dropped and the call blocked until an external
 * wall-clock timeout. These tests drive the same exchange against a scripted fake
 * relay and assert the client now fails fast: [negentropySync] throws and
 * [negentropySyncOrFetch] falls back to paging.
 */
class NegentropyRejectionFallbackTest {
    private val url = NormalizedRelayUrl("wss://reject.example.com")

    /** `["REQ","<subId>",…]` → `<subId>`; also matches NEG-OPEN's subId slot. */
    private fun subIdOf(frame: String): String? = Regex("^\\[\"[A-Z-]+\",\"([^\"]+)\"").find(frame)?.groupValues?.get(1)

    /**
     * A fake relay whose reply to a NEG-OPEN is produced by [replyToNegOpen] (given the
     * NEG-OPEN's subId), counting NEG-OPENs so a test can assert the client did NOT
     * split-storm. All replies (and the initial onOpen) are posted on a background
     * executor so the socket behaves like a real async OkHttp socket — never re-entrant
     * into the client's send path.
     */
    private inner class ScriptedRelay(
        val replyToNegOpen: (subId: String) -> String,
    ) : WebsocketBuilder {
        val io = Executors.newSingleThreadScheduledExecutor()
        val negOpens = AtomicInteger(0)

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket =
            object : WebSocket {
                override fun needsReconnect() = false

                override fun connect() {
                    io.schedule({ out.onOpen(10, false) }, 5, TimeUnit.MILLISECONDS)
                }

                override fun disconnect() {}

                override fun send(msg: String): Boolean {
                    io.schedule({
                        when {
                            // The keep-alive REQ and any paging REQ: answer EOSE so the
                            // subscription settles (paging then completes with 0 events).
                            msg.startsWith("[\"REQ\"") -> subIdOf(msg)?.let { kotlinx.coroutines.runBlocking { out.onMessage("[\"EOSE\",\"$it\"]") } }
                            // The negentropy handshake: the relay refuses.
                            msg.startsWith("[\"NEG-OPEN\"") -> {
                                negOpens.incrementAndGet()
                                subIdOf(msg)?.let { kotlinx.coroutines.runBlocking { out.onMessage(replyToNegOpen(it)) } }
                            }
                            else -> Unit
                        }
                    }, 5, TimeUnit.MILLISECONDS)
                    return true
                }
            }

        fun shutdown() = io.shutdownNow()
    }

    private fun negOpenRejectedBy(reply: (subId: String) -> String): ScriptedRelay {
        val relay = ScriptedRelay(reply)
        val client = NostrClient(relay)
        try {
            runBlocking {
                // negentropySync must fail fast (throw), not hang.
                val thrown =
                    assertFailsWith<NegentropySyncException> {
                        withTimeout(8_000) {
                            client.negentropySync(url, Filter(kinds = listOf(0))) { }
                        }
                    }
                assertTrue(
                    thrown.reason == NegentropySyncException.Reason.UNAVAILABLE,
                    "a runtime negentropy refusal should be UNAVAILABLE, was ${thrown.reason}",
                )

                // negentropySyncOrFetch must transparently fall back to paging.
                val result =
                    withTimeout(8_000) {
                        client.negentropySyncOrFetch(url, Filter(kinds = listOf(0))) { }
                    }
                assertTrue(result.pagedFallback, "expected paging fallback after the refusal")
                assertEquals(0, result.downloaded)
            }
        } finally {
            client.close()
            relay.shutdown()
        }
        return relay
    }

    @Test
    fun strfryNegentropyDisabledFallsBackToPaging() =
        kotlinx.coroutines.test.runTest {
            negOpenRejectedBy { "[\"NOTICE\",\"ERROR: bad msg: negentropy disabled\"]" }
        }

    @Test
    fun purplePagesUnknownEnvelopeFallsBackToPaging() =
        kotlinx.coroutines.test.runTest {
            negOpenRejectedBy { "[\"NOTICE\",\"failed to parse envelope: unknown envelope label\"]" }
        }

    @Test
    fun rateLimitNegErrPagesWithoutSplitStorm() =
        kotlinx.coroutines.test.runTest {
            // A NEG-ERR that does NOT shrink with the window ("too many requests") must not
            // be mistaken for a set-too-large overflow: doing so would binary-split the
            // created_at range forever. Assert we page after exactly ONE NEG-OPEN.
            val relay = negOpenRejectedBy { subId -> "[\"NEG-ERR\",\"$subId\",\"rate-limited: too many requests\"]" }
            assertEquals(
                2,
                relay.negOpens.get(),
                "one NEG-OPEN per phase (sync + syncOrFetch), i.e. no window-split storm; got ${relay.negOpens.get()}",
            )
        }
}
