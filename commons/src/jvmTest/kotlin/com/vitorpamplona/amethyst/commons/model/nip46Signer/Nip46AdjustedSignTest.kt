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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.domain.nip46.NostrConnectLoginUseCase
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Amethyst's bunker ([Nip46SignerState]) answering Amethyst's own NIP-46 client
 * ([com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote]) when the key behind the
 * bunker signs something other than the exact template, as some NDK-based bunkers do. The client
 * takes an answer validly signed under the user's key and of the kind it asked for, whatever its
 * tags or `created_at`; an answer of another kind, or under another key, fails the sign.
 */
class Nip46AdjustedSignTest {
    private val appRelay = NormalizedRelayUrl("wss://app-relay.example.com/")
    private val inboxRelay = NormalizedRelayUrl("wss://inbox.example.com/")

    private enum class Adjust { ADD_TAG, MOVE_CREATED_AT, OTHER_KIND, OTHER_KEY }

    /** The user's key, signing each template after [adjust] changed it. */
    private class AdjustingSigner(
        private val inner: NostrSignerInternal,
        private val adjust: Adjust,
    ) : NostrSigner(inner.pubKey) {
        private val stranger = NostrSignerInternal(KeyPair())

        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T =
            when (adjust) {
                Adjust.ADD_TAG -> inner.sign(createdAt, kind, tags + arrayOf(arrayOf("client", "ndk-bunker")), content)
                Adjust.MOVE_CREATED_AT -> inner.sign(createdAt + 7, kind, tags, content)
                Adjust.OTHER_KIND -> inner.sign(createdAt, kind + 1, tags, content)
                Adjust.OTHER_KEY -> stranger.sign(createdAt, kind, tags, content)
            }

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip44Decrypt(ciphertext, fromPublicKey)

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    /** Pairs an app with a bunker whose key signs through [adjust], then has the app sign a note. */
    private fun signThrough(adjust: Adjust): Pair<String, Event> =
        runBlocking(Dispatchers.Default) {
            val net = Nip46SignerTestNet(listOf(appRelay, inboxRelay))
            try {
                val state =
                    Nip46SignerState(
                        signer = AdjustingSigner(net.identity, adjust),
                        client = net.client,
                        ledger = net.ledger,
                        clientStore = net.clientStore,
                        inboxRelays = MutableStateFlow(setOf(inboxRelay)),
                        scope = net.scope,
                        settings = AccountSettings(net.identity.keyPair),
                        consent = Nip46ConsentPrompter.Unanswered,
                        relayAcceptsSignerTraffic = { true },
                    )

                val code = NostrConnectLoginUseCase.generateUri(KeyPair(), listOf(appRelay.url), BunkerClientMetadata(name = "App"))
                val coordinate = Nip46PermissionAuthorizer.coordinateFor(net.identity.pubKey, code.ephemeralKeyPair.pubKey.toHexKey())
                net.ledger.updateLastUsed(coordinate)
                net.ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)

                val login = net.scope.async { NostrConnectLoginUseCase.awaitAndLogin(code, net.client) }
                delay(1_000)
                state.connectViaNostrConnect(code.uri)
                val app = withTimeout(20_000) { login.await() }
                assertEquals(net.identity.pubKey, app.pubKeyHex)

                try {
                    val signed: Event = withTimeout(30_000) { app.signer.sign(TextNoteEvent.build("hello from the app")) }
                    net.identity.pubKey to signed
                } finally {
                    app.signer.closeSubscription()
                }
            } finally {
                net.close()
            }
        }

    @Test
    fun aBunkerThatAddsATagStillSigns() {
        val (user, signed) = signThrough(Adjust.ADD_TAG)
        assertTrue(signed.verify())
        assertEquals(user, signed.pubKey)
        assertEquals(TextNoteEvent.KIND, signed.kind)
        assertTrue(signed.tags.any { it.getOrNull(0) == "client" && it.getOrNull(1) == "ndk-bunker" })
    }

    @Test
    fun aBunkerThatMovesCreatedAtStillSigns() {
        val (user, signed) = signThrough(Adjust.MOVE_CREATED_AT)
        assertTrue(signed.verify())
        assertEquals(user, signed.pubKey)
        assertEquals("hello from the app", signed.content)
    }

    /** Refused on arrival: not a timeout, which is an IllegalStateException too. */
    private fun assertRefused(adjust: Adjust) {
        val e = assertFailsWith<IllegalStateException> { signThrough(adjust) }
        assertFalse(e is CancellationException, "the sign timed out instead of failing: $e")
        assertTrue(e.message.orEmpty().contains("Failed to verify event"), "refused as unverifiable: $e")
    }

    @Test
    fun anAnswerOfAnotherKindFails() = assertRefused(Adjust.OTHER_KIND)

    @Test
    fun anAnswerUnderAnotherKeyFails() = assertRefused(Adjust.OTHER_KEY)
}
