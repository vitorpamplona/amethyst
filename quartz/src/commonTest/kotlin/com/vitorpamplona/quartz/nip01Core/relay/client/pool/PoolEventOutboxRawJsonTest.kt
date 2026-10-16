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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An event published with its own JSON ([PoolEventOutbox.markAsSending]'s `eventJson`) goes out
 * as that JSON on every send the outbox makes: the first, the replay on reconnect, and the
 * re-send after a `rate-limited:` back-off.
 */
class PoolEventOutboxRawJsonTest {
    private val relay = NormalizedRelayUrl("wss://raw.relay.test")

    private val event =
        Event(
            id = "aa".repeat(32),
            pubKey = "00".repeat(32),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = emptyArray(),
            content = "hello",
            sig = "00".repeat(64),
        )

    // Not what the serializer would write (key order, spacing): only a verbatim send yields it.
    private val json = """{"sig": "${event.sig}", "id": "${event.id}", "pubkey": "${event.pubKey}", "created_at": 1700000000, "kind": 1, "tags": [], "content": "hello"}"""

    private fun sentBy(block: ((Command) -> Unit) -> Unit): List<String> {
        val frames = mutableListOf<String>()
        block { frames.add(it.toJson()) }
        return frames
    }

    @Test
    fun theFirstSendCarriesTheJson() {
        val outbox = PoolEventOutbox()
        val relays = outbox.markAsSending(event, setOf(relay), json)
        val frames = sentBy { send -> outbox.sendToRelayIfChanged(event, relays) { _, cmd -> send(cmd) } }
        assertEquals(listOf("[\"EVENT\",$json]"), frames)
    }

    @Test
    fun theReplayOnReconnectCarriesTheJson() =
        runTest {
            val outbox = PoolEventOutbox()
            outbox.markAsSending(event, setOf(relay), json)
            val frames = mutableListOf<String>()
            outbox.syncState(relay) { frames.add(it.toJson()) }
            assertEquals(listOf("[\"EVENT\",$json]"), frames)
        }

    @Test
    fun theRateLimitedResendCarriesTheJson() {
        val outbox = PoolEventOutbox()
        outbox.markAsSending(event, setOf(relay), json)
        val frames = sentBy { send -> outbox.retryRateLimited(relay, setOf(event.id), send) }
        assertEquals(listOf("[\"EVENT\",$json]"), frames)
    }

    @Test
    fun aLaterPlainPublishKeepsTheJson() {
        val outbox = PoolEventOutbox()
        outbox.markAsSending(event, setOf(relay), json)
        outbox.markAsSending(event, setOf(relay))
        val frames = sentBy { send -> outbox.retryRateLimited(relay, setOf(event.id), send) }
        assertEquals(listOf("[\"EVENT\",$json]"), frames)
    }

    @Test
    fun withoutJsonTheEventIsSerialized() {
        assertEquals(EventCmd(event).toJson(), EventCmd(event, null).toJson())
        assertEquals("[\"EVENT\",${event.toJson()}]", EventCmd(event).toJson())
    }
}
