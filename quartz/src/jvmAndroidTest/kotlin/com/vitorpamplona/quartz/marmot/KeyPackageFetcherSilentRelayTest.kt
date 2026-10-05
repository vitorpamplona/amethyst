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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Adding a Marmot member took most of a minute: the KeyPackage lookup drains every relay to EOSE,
 * and the set it asks unions the invitee's relays with ours, so one relay that never answers held
 * every invite for the whole 30s idle window.
 */
class KeyPackageFetcherSilentRelayTest {
    private val hub = InProcessRelays()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val silent: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7772/")

    @AfterTest
    fun tearDown() {
        scope.cancel()
        hub.close()
    }

    /** The hub, except [silent] swallows every REQ: no events, no EOSE, no CLOSED. */
    private inner class OneSilentRelay : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            val inner = hub.build(url, out)
            if (url != silent) return inner
            return object : WebSocket by inner {
                override fun send(msg: String): Boolean = if (msg.startsWith("[\"REQ\"")) true else inner.send(msg)
            }
        }
    }

    private fun keyPackage(
        signer: NostrSignerInternal,
        slot: String,
        createdAt: Long,
    ) = runBlocking {
        signer.sign(
            KeyPackageEvent.build(
                keyPackageBase64 = "AA==",
                dTagSlot = slot,
                keyPackageRef = "00".repeat(32),
                relays = listOf(InProcessRelays.DEFAULT_URL),
                createdAt = createdAt,
            ),
        )
    }

    @Test
    fun aSilentRelayDoesNotHoldTheLookupAndTheNewestPackageStillWins() =
        runBlocking {
            val client = NostrClient(OneSilentRelay(), scope)
            val invitee = NostrSignerInternal(KeyPair())
            val now = TimeUtils.now()
            val older = keyPackage(invitee, "a", now - 3600)
            val newer = keyPackage(invitee, "b", now)
            assertTrue(client.publishAndConfirm(older, setOf(InProcessRelays.DEFAULT_URL)))
            assertTrue(client.publishAndConfirm(newer, setOf(InProcessRelays.DEFAULT_URL)))

            val (found, elapsed) =
                measureTimedValue {
                    KeyPackageFetcher.fetchKeyPackage(
                        client = client,
                        targetPubKey = invitee.pubKey,
                        relays = setOf(InProcessRelays.DEFAULT_URL, silent),
                        idleTimeoutMs = 10_000,
                        settleAfterFirstMs = 500,
                    )
                }

            assertEquals(newer.id, found?.id, "the newest KeyPackage is still the one returned")
            assertTrue(elapsed.inWholeMilliseconds < 3_000, "stopped shortly after the first package, not at the 10s idle window (took $elapsed)")
            client.disconnect()
        }
}
