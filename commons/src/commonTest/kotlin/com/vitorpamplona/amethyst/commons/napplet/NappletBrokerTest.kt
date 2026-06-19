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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NappletBrokerTest {
    private val userPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(userPriv.hexToByteArray()))

    private val strangerPriv = "0000000000000000000000000000000000000000000000000000000000000019"
    private val stranger = NostrSignerInternal(KeyPair(strangerPriv.hexToByteArray()))

    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")

    private class ScriptedPrompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        var calls = 0
            private set

        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState {
            calls++
            return answer
        }
    }

    private class RecordingRelay : NappletRelayGateway {
        val published = mutableListOf<Event>()

        override suspend fun publish(event: Event): List<String> {
            published.add(event)
            return listOf("wss://relay.example")
        }
    }

    private fun broker(
        prompt: NappletConsentPrompt,
        relay: NappletRelayGateway? = null,
        ledger: NappletPermissionLedger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
    ) = NappletBroker(signer, ledger, prompt, relay)

    @Test
    fun getPublicKeyReturnsTheUsersKeyWhenAllowed() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, NappletRequest.GetPublicKey)
            assertEquals(NappletResponse.PublicKey(signer.pubKey), response)
        }

    @Test
    fun standingDenyBlocksWithoutPrompting() =
        runTest {
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.DENY)
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)

            val response = broker(prompt, ledger = ledger).handle(applet, NappletRequest.GetPublicKey)

            assertIs<NappletResponse.Denied>(response)
            assertEquals(0, prompt.calls) // never asked the user — the standing DENY is authoritative
        }

    @Test
    fun userDeclineYieldsDenied() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.DENY)).handle(applet, NappletRequest.GetPublicKey)
            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun allowAlwaysPromptsOnceThenRemembers() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)
            val broker = broker(prompt)

            broker.handle(applet, NappletRequest.GetPublicKey)
            broker.handle(applet, NappletRequest.GetPublicKey)
            broker.handle(applet, NappletRequest.GetPublicKey)

            assertEquals(1, prompt.calls) // only the first request prompted
        }

    @Test
    fun allowOncePromptsEveryTime() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ONCE)
            val broker = broker(prompt)

            broker.handle(applet, NappletRequest.GetPublicKey)
            broker.handle(applet, NappletRequest.GetPublicKey)

            assertEquals(2, prompt.calls)
        }

    @Test
    fun signEventProducesAValidlySignedEventAsTheUser() =
        runTest {
            val request = NappletRequest.SignEvent(kind = 1, tags = arrayOf(arrayOf("t", "napplet")), content = "gm")
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, request)

            assertIs<NappletResponse.SignedEvent>(response)
            val event = response.event
            assertEquals(signer.pubKey, event.pubKey) // applet cannot sign as anyone but the user
            assertEquals(1, event.kind)
            assertEquals("gm", event.content)
            assertTrue(event.verify()) // id + signature are real
        }

    @Test
    fun publishWithoutAGatewayIsUnsupported() =
        runTest {
            val event: Event = signer.sign(1L, 1, emptyArray(), "hi")
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = null)
                    .handle(applet, NappletRequest.Publish(event))
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun publishRefusesAnEventFromAnotherIdentity() =
        runTest {
            val foreign: Event = stranger.sign(1L, 1, emptyArray(), "not yours")
            val relay = RecordingRelay()

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay)
                    .handle(applet, NappletRequest.Publish(foreign))

            assertIs<NappletResponse.Failed>(response)
            assertTrue(relay.published.isEmpty()) // nothing left the broker
        }

    @Test
    fun publishSendsAValidUserEventThroughTheGateway() =
        runTest {
            val event: Event = signer.sign(1L, 1, emptyArray(), "ship it")
            val relay = RecordingRelay()

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay)
                    .handle(applet, NappletRequest.Publish(event))

            assertEquals(NappletResponse.Published(listOf("wss://relay.example")), response)
            assertEquals(1, relay.published.size)
        }

    @Test
    fun relayDenyDoesNotReachTheGateway() =
        runTest {
            val event: Event = signer.sign(1L, 1, emptyArray(), "blocked")
            val relay = RecordingRelay()

            val response =
                broker(ScriptedPrompt(GrantState.DENY), relay = relay)
                    .handle(applet, NappletRequest.Publish(event))

            assertIs<NappletResponse.Denied>(response)
            assertTrue(relay.published.isEmpty())
        }
}
