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
import com.vitorpamplona.amethyst.commons.napplet.permissions.PermissionDecision
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletBrokerTest {
    private val userPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(userPriv.hexToByteArray()))

    private val strangerPriv = "0000000000000000000000000000000000000000000000000000000000000019"
    private val stranger = NostrSignerInternal(KeyPair(strangerPriv.hexToByteArray()))

    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")

    private val allDeclared = NappletCapability.entries.toSet()

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

    private class RecordingRelay(
        private val queryResult: List<Event> = emptyList(),
    ) : NappletRelayGateway {
        val published = mutableListOf<Event>()

        override suspend fun publish(event: Event): List<String> {
            published.add(event)
            return listOf("wss://relay.example")
        }

        override suspend fun query(filter: Filter): List<Event> = queryResult
    }

    private class RecordingWallet(
        private val preimage: String? = "preimage",
    ) : NappletWalletGateway {
        var calls = 0
            private set

        override suspend fun payInvoice(invoice: String): String? {
            calls++
            return preimage
        }
    }

    /** A non-internal signer that stands in for a remote (NIP-46) / external (NIP-55) signer. */
    private class FakeExternalSigner(
        pubKey: HexKey,
    ) : NostrSigner(pubKey) {
        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = throw NotImplementedError()

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = throw NotImplementedError()

        override suspend fun deriveKey(nonce: HexKey): HexKey = throw NotImplementedError()

        override suspend fun signPsbt(psbtHex: String): String = throw NotImplementedError()

        override fun hasForegroundSupport() = true
    }

    private class MapStorage : NappletStorage {
        val data = mutableMapOf<String, String>()

        private fun k(
            coordinate: String,
            key: String,
        ) = "$coordinate::$key"

        override suspend fun get(
            coordinate: String,
            key: String,
        ): String? = data[k(coordinate, key)]

        override suspend fun set(
            coordinate: String,
            key: String,
            value: String,
        ) {
            data[k(coordinate, key)] = value
        }

        override suspend fun remove(
            coordinate: String,
            key: String,
        ) {
            data.remove(k(coordinate, key))
        }
    }

    private fun broker(
        prompt: NappletConsentPrompt,
        relay: NappletRelayGateway? = null,
        storage: NappletStorage? = null,
        wallet: NappletWalletGateway? = null,
        signer: NostrSigner = this.signer,
        ledger: NappletPermissionLedger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
    ) = NappletBroker(signer, ledger, prompt, relay, storage, wallet)

    @Test
    fun getPublicKeyReturnsTheUsersKeyWhenAllowed() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, NappletRequest.GetPublicKey, allDeclared)
            assertEquals(NappletResponse.PublicKey(signer.pubKey), response)
        }

    @Test
    fun undeclaredCapabilityIsDeniedWithoutPrompting() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)

            // The manifest declared only RELAY, but the applet asks to sign (IDENTITY).
            val response = broker(prompt).handle(applet, NappletRequest.GetPublicKey, setOf(NappletCapability.RELAY))

            assertIs<NappletResponse.Denied>(response)
            assertEquals(0, prompt.calls) // never reaches the user — refused at the declaration gate
        }

    @Test
    fun standingDenyBlocksWithoutPrompting() =
        runTest {
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.DENY)
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)

            val response = broker(prompt, ledger = ledger).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertIs<NappletResponse.Denied>(response)
            assertEquals(0, prompt.calls) // never asked the user — the standing DENY is authoritative
        }

    @Test
    fun userDeclineYieldsDenied() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.DENY)).handle(applet, NappletRequest.GetPublicKey, allDeclared)
            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun allowAlwaysPromptsOnceThenRemembers() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)
            val broker = broker(prompt)

            broker.handle(applet, NappletRequest.GetPublicKey, allDeclared)
            broker.handle(applet, NappletRequest.GetPublicKey, allDeclared)
            broker.handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertEquals(1, prompt.calls) // only the first request prompted
        }

    @Test
    fun signEventProducesAValidlySignedEventAsTheUser() =
        runTest {
            val request = NappletRequest.SignEvent(kind = 1, tags = arrayOf(arrayOf("t", "napplet")), content = "gm")
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, request, allDeclared)

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
                    .handle(applet, NappletRequest.Publish(event), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun publishRefusesAnEventFromAnotherIdentity() =
        runTest {
            val foreign: Event = stranger.sign(1L, 1, emptyArray(), "not yours")
            val relay = RecordingRelay()

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay)
                    .handle(applet, NappletRequest.Publish(foreign), allDeclared)

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
                    .handle(applet, NappletRequest.Publish(event), allDeclared)

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
                    .handle(applet, NappletRequest.Publish(event), allDeclared)

            assertIs<NappletResponse.Denied>(response)
            assertTrue(relay.published.isEmpty())
        }

    @Test
    fun queryReturnsTheGatewayResults() =
        runTest {
            val cached: Event = signer.sign(1L, 1, emptyArray(), "cached")
            val relay = RecordingRelay(queryResult = listOf(cached))

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay)
                    .handle(applet, NappletRequest.QueryEvents(Filter(kinds = listOf(1))), allDeclared)

            assertEquals(NappletResponse.Events(listOf(cached)), response)
        }

    @Test
    fun storageRoundTripsAndIsScopedToTheApplet() =
        runTest {
            val storage = MapStorage()
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), storage = storage)

            assertEquals(NappletResponse.Done, broker.handle(applet, NappletRequest.StorageSet("k", "v"), allDeclared))
            assertEquals(NappletResponse.StorageValue("v"), broker.handle(applet, NappletRequest.StorageGet("k"), allDeclared))

            // Stored under the applet coordinate, never a shared namespace.
            assertEquals("v", storage.data["${applet.coordinate}::k"])

            assertEquals(NappletResponse.Done, broker.handle(applet, NappletRequest.StorageRemove("k"), allDeclared))
            assertEquals(NappletResponse.StorageValue(null), broker.handle(applet, NappletRequest.StorageGet("k"), allDeclared))
        }

    @Test
    fun storageWithoutAGatewayIsUnsupported() =
        runTest {
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), storage = null)
                    .handle(applet, NappletRequest.StorageGet("k"), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun walletWithoutAGatewayIsUnsupported() =
        runTest {
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE))
                    .handle(applet, NappletRequest.PayInvoice("lnbc1..."), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
            assertNull((response as? NappletResponse.Paid)?.preimage)
        }

    @Test
    fun walletConfirmsEveryPaymentEvenAfterAllowAlways() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS) // user keeps tapping "always"
            val wallet = RecordingWallet()
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val broker = broker(prompt, wallet = wallet, ledger = ledger)

            broker.handle(applet, NappletRequest.PayInvoice("lnbc1"), allDeclared)
            broker.handle(applet, NappletRequest.PayInvoice("lnbc1"), allDeclared)

            assertEquals(2, prompt.calls) // every payment prompts
            assertEquals(2, wallet.calls)
            // ALLOW_ALWAYS must not have been persisted for a per-use capability.
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.WALLET))
        }

    @Test
    fun externalSignerDefersIdentityWithoutPrompting() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.DENY) // would block if we asked
            val external = FakeExternalSigner("dd".repeat(32))

            val response =
                broker(prompt, signer = external).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertEquals(NappletResponse.PublicKey(external.pubKey), response)
            assertEquals(0, prompt.calls) // deferred to the external signer; we did not prompt
        }

    @Test
    fun externalSignerStillHonorsAStandingDeny() =
        runTest {
            val external = FakeExternalSigner("dd".repeat(32))
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.DENY)

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), signer = external, ledger = ledger)
                    .handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertIs<NappletResponse.Denied>(response)
        }
}
