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

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletBrokerTest {
    private val userPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(userPriv.hexToByteArray()))

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

        override suspend fun query(filters: List<Filter>): List<Event> = queryResult
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

        override suspend fun keys(coordinate: String): List<String> = data.keys.filter { it.startsWith("$coordinate::") }.map { it.removePrefix("$coordinate::") }
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

    /** A prompt that blocks inside [request] until [gate] is released, tracking peak concurrency. */
    private class GatedPrompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
            private set
        var maxActive = 0
            private set
        private var active = 0

        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState {
            calls++
            active++
            if (active > maxActive) maxActive = active
            try {
                gate.await()
                return answer
            } finally {
                active--
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun concurrentRequestsForOneCapabilitySerializeIntoASinglePrompt() =
        runTest {
            // Five capability calls land at once (a napplet hitting relays/storage/identity on load).
            // Without serialization each would launch its own consent prompt concurrently; the host
            // can only show one, so the rest are dropped and hang. The broker must queue them through
            // a single prompt, and siblings must honor the grant the first one records.
            val prompt = GatedPrompt(GrantState.ALLOW_ALWAYS)
            val broker = broker(prompt)

            val jobs = List(5) { async { broker.handle(applet, NappletRequest.GetPublicKey, allDeclared) } }
            advanceUntilIdle() // everyone reaches the gate/lock; only the lock holder is inside request()

            assertEquals(1, prompt.maxActive) // never two prompts at once (the bug would show 5)

            prompt.gate.complete(Unit)
            val responses = jobs.awaitAll()

            assertEquals(1, prompt.calls) // siblings honored the recorded ALLOW_ALWAYS instead of re-prompting
            responses.forEach { assertEquals(NappletResponse.PublicKey(signer.pubKey), it) }
        }

    /** A first-connect prompt that returns a scripted sequence of answers and counts its calls. */
    private class ScriptedConnectPrompt(
        private vararg val answers: AppConnectResult,
    ) : NostrConnectPrompt {
        var calls = 0
            private set

        /** The capabilities the last call was asked to disclose, so tests can assert the dialog got them. */
        var lastDeclared: Set<NappletCapability> = emptySet()
            private set

        override suspend fun request(
            identity: NappletIdentity,
            declared: Set<NappletCapability>,
        ): AppConnectResult {
            val answer = answers[minOf(calls, answers.size - 1)]
            calls++
            lastDeclared = declared
            return answer
        }
    }

    @Test
    fun aGrantFromOneAccountNeverAuthorizesTheSameAppUnderAnother() =
        runTest {
            // The signer permission store is shared across accounts (and with NIP-46), so the napplet
            // coordinate must carry the account. Without that, "always allow" granted by one npub
            // silently authorized signing under every other npub on the device — which would defeat
            // the point of keeping a pseudonymous account separate from a real one.
            val store = InMemoryNostrSignerPermissionStore()
            val otherSigner = NostrSignerInternal(KeyPair())

            fun brokerFor(who: NostrSignerInternal) =
                NappletBroker(
                    signer = who,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = NostrSignerPermissionLedger(store),
                    nostrConnectPrompt = ScriptedConnectPrompt(AppConnectResult.Connected(AppSignerPolicy.FULL_TRUST)),
                )

            // Account A connects the applet and fully trusts it.
            assertEquals(
                NappletResponse.PublicKey(signer.pubKey),
                brokerFor(signer).handle(applet, NappletRequest.GetPublicKey, allDeclared),
            )
            assertEquals(AppSignerPolicy.FULL_TRUST, store.loadPolicy("napplet:${signer.pubKey}:${applet.coordinate}"))

            // Account B has granted the very same applet nothing.
            assertNull(store.loadPolicy("napplet:${otherSigner.pubKey}:${applet.coordinate}"))

            // ...so B's broker must run its own first-connect flow rather than inheriting A's trust.
            val bConnect = ScriptedConnectPrompt(AppConnectResult.Cancelled)
            val bBroker =
                NappletBroker(
                    signer = otherSigner,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = NostrSignerPermissionLedger(store),
                    nostrConnectPrompt = bConnect,
                )
            assertIs<NappletResponse.Denied>(bBroker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(1, bConnect.calls)
        }

    @Test
    fun cancellingFirstConnectThenRetryingRePromptsOnceCooldownLapses() =
        runTest {
            // Repro for the reported bug: dismissing the "Connect to Nostr" dialog (Back) used to
            // suppress every future prompt for the broker's whole lifetime, with nothing in Connected
            // Apps to clear. The user could never re-trigger login. After the fix the suppression is a
            // short self-clearing cooldown: a deliberate retry past it prompts again.
            var clock = 1_000L
            val connect = ScriptedConnectPrompt(AppConnectResult.Cancelled, AppConnectResult.Connected(AppSignerPolicy.REASONABLE))
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.DENY),
                    signerLedger = signerLedger,
                    nostrConnectPrompt = connect,
                    nowMillis = { clock },
                )

            // 1. User dismisses the dialog → Denied, and no policy is persisted (nothing to clear).
            assertIs<NappletResponse.Denied>(broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertNull(signerLedger.store.loadPolicy(applet.coordinate))

            // 2. A request inside the cooldown is suppressed silently (no second dialog) — this drains
            //    the load-time burst without re-prompting per request.
            assertIs<NappletResponse.Denied>(broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(1, connect.calls)

            // 3. The user retries after the cooldown lapses → the dialog shows again and they connect.
            clock += 10_000L
            assertEquals(NappletResponse.PublicKey(signer.pubKey), broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(2, connect.calls)
            // Signer grants are stored under the account-scoped coordinate, not the bare one.
            assertEquals(AppSignerPolicy.REASONABLE, signerLedger.store.loadPolicy("napplet:${signer.pubKey}:${applet.coordinate}"))
        }

    @Test
    fun publishSignsTheTemplateAsTheUserAndSends() =
        runTest {
            val relay = RecordingRelay()
            val request = NappletRequest.Publish(kind = 1, tags = arrayOf(arrayOf("t", "napplet")), content = "gm")

            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay).handle(applet, request, allDeclared)

            // The shell signs the unsigned template and resolves to the signed event + relays.
            assertIs<NappletResponse.Published>(response)
            val event = response.event
            assertEquals(signer.pubKey, event.pubKey) // applet supplied no pubkey; the shell fixed it to the user
            assertEquals(1, event.kind)
            assertEquals("gm", event.content)
            assertTrue(event.verify()) // id + signature are real
            assertEquals(listOf("wss://relay.example"), response.relays)
            assertEquals(1, relay.published.size)
            assertEquals(event.id, relay.published.first().id)
        }

    @Test
    fun publishWithoutAGatewayIsUnsupported() =
        runTest {
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = null)
                    .handle(applet, NappletRequest.Publish(1, emptyArray(), "hi"), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun relayDenyDoesNotReachTheGateway() =
        runTest {
            val relay = RecordingRelay()

            val response =
                broker(ScriptedPrompt(GrantState.DENY), relay = relay)
                    .handle(applet, NappletRequest.Publish(1, emptyArray(), "blocked"), allDeclared)

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
                    .handle(applet, NappletRequest.QueryEvents(listOf(Filter(kinds = listOf(1)))), allDeclared)

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
    fun storageKeysListsOnlyThisAppletsKeys() =
        runTest {
            val storage = MapStorage()
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), storage = storage)
            val other = NappletIdentity(authorPubKey = "bb".repeat(32), identifier = "other")

            broker.handle(applet, NappletRequest.StorageSet("a", "1"), allDeclared)
            broker.handle(applet, NappletRequest.StorageSet("b", "2"), allDeclared)
            broker.handle(other, NappletRequest.StorageSet("c", "3"), allDeclared)

            val response = broker.handle(applet, NappletRequest.StorageKeys, allDeclared)
            assertIs<NappletResponse.Strings>(response)
            assertEquals(setOf("a", "b"), response.values.toSet()) // never sees the other applet's "c"
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
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.VALUE))
        }

    @Test
    fun externalSignerIsPromptedByAmethystForIdentity() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.DENY)
            val external = FakeExternalSigner("dd".repeat(32))

            val response =
                broker(prompt, signer = external).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertIs<NappletResponse.Denied>(response) // Amethyst asked and user denied
            assertEquals(1, prompt.calls) // Amethyst prompts all signer types
        }

    @Test
    fun externalSignerAllowedByAmethystReturnsPublicKey() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ONCE)
            val external = FakeExternalSigner("dd".repeat(32))

            val response =
                broker(prompt, signer = external).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertEquals(NappletResponse.PublicKey(external.pubKey), response)
            assertEquals(1, prompt.calls) // Amethyst prompted before the external signer
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

    @Test
    fun shellSupportsReflectsDeclaredCapabilitiesWithoutConsent() =
        runTest {
            // The DENY prompt would block anything that reached consent; supports must not.
            val broker = broker(ScriptedPrompt(GrantState.DENY))
            val declared = setOf(NappletCapability.RELAY)

            assertEquals(NappletResponse.Supported(true), broker.handle(applet, NappletRequest.ShellSupports("relay"), declared))
            assertEquals(NappletResponse.Supported(false), broker.handle(applet, NappletRequest.ShellSupports("storage"), declared))
            // Unknown/unbrokered domain.
            assertEquals(NappletResponse.Supported(false), broker.handle(applet, NappletRequest.ShellSupports("cvm"), declared))
        }

    @Test
    fun identityReadReturnsGatewayJsonOrUnsupported() =
        runTest {
            val gateway =
                NappletIdentityGateway { method, _ ->
                    if (method == "getFollows") """["aa","bb"]""" else null
                }
            val broker =
                NappletBroker(
                    signer,
                    NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    ScriptedPrompt(GrantState.ALLOW_ONCE),
                    identityReads = gateway,
                )

            val implemented = broker.handle(applet, NappletRequest.IdentityRead("getFollows"), allDeclared)
            assertIs<NappletResponse.Json>(implemented)
            assertEquals("""["aa","bb"]""", implemented.raw)

            // A method the gateway doesn't implement degrades gracefully to Unsupported.
            assertIs<NappletResponse.Unsupported>(broker.handle(applet, NappletRequest.IdentityRead("getZaps"), allDeclared))
        }

    @Test
    fun identityReadIsUnsupportedWithoutAGateway() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, NappletRequest.IdentityRead("getProfile"), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun resourceAndUploadAreUnsupportedWithoutGateways() =
        runTest {
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ONCE))
            assertIs<NappletResponse.Unsupported>(broker.handle(applet, NappletRequest.ResourceBytes("https://x"), allDeclared))
            assertIs<NappletResponse.Unsupported>(broker.handle(applet, NappletRequest.UploadBlob(ByteArray(0), "image/png"), allDeclared))
        }
}
