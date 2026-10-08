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
package com.vitorpamplona.quartz.experimental.decoupling.transfer

import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-4E key sync: kind 4454 requests and kind 4455 transfers. The request fixture has the shape of
 * the PsstPsst requests seen on relays (`P` + legacy `pubkey` alias + `relay` + `n`), with synthetic
 * keys. Transfers are exercised end to end with fresh key pairs.
 */
class EncryptionKeyTransferEventsTest {
    private val identity = "a".repeat(64)
    private val requesterClient = "5f9c73f77c84b043fb1e505f7afea35beeefb05fdacf76ce872a6a3eccd0049e"
    private val encryptionPub = "e".repeat(64)

    private fun request(tags: Array<Array<String>>): Event = EventFactory.create("1".repeat(64), identity, 1_791_312_273L, EncryptionKeyRequestEvent.KIND, tags, "", "00".repeat(64))

    private fun isSearchable(event: Event) = event is SearchableEvent

    private fun <T : Event> EventTemplate<T>.toEvent(author: HexKey): T = EventFactory.create("2".repeat(64), author, createdAt, kind, tags, content, "00".repeat(64))

    @Test
    fun factoryBuildsBothKinds() {
        assertIs<EncryptionKeyRequestEvent>(request(emptyArray()))
        assertIs<EncryptionKeyTransferEvent>(EventFactory.create<Event>("1".repeat(64), identity, 1L, EncryptionKeyTransferEvent.KIND, emptyArray(), "", ""))
        assertTrue(EventFactory.isKnownKind(EncryptionKeyRequestEvent.KIND))
        assertTrue(EventFactory.isKnownKind(EncryptionKeyTransferEvent.KIND))
    }

    @Test
    fun parsesAPsstPsstRequest() {
        val event =
            assertIs<EncryptionKeyRequestEvent>(
                request(
                    arrayOf(
                        arrayOf("P", requesterClient),
                        arrayOf("pubkey", requesterClient),
                        arrayOf("relay", "wss://relay.example.com"),
                        arrayOf("n", encryptionPub),
                        arrayOf("client", "Tortilla on Android"),
                    ),
                ),
            )

        assertEquals(requesterClient, event.clientKey())
        assertTrue(event.hasConsistentClientKey())
        assertEquals(listOf("wss://relay.example.com/"), event.responseRelays().map { it.url })
        assertEquals(encryptionPub, event.requestedEncryptionKey())
        assertEquals("Tortilla on Android", event.clientName())
        assertEquals("5F9C 73F7", event.authorizationCode())
    }

    @Test
    fun requestFallsBackToTheLegacyAliasAndFlagsAMismatch() {
        val legacy = assertIs<EncryptionKeyRequestEvent>(request(arrayOf(arrayOf("pubkey", requesterClient))))
        assertEquals(requesterClient, legacy.clientKey())
        assertNull(legacy.requestedEncryptionKey())

        val mismatch = assertIs<EncryptionKeyRequestEvent>(request(arrayOf(arrayOf("P", requesterClient), arrayOf("pubkey", "b".repeat(64)))))
        assertEquals(requesterClient, mismatch.clientKey())
        assertFalse(mismatch.hasConsistentClientKey())
    }

    @Test
    fun malformedRequestTagsAreSkipped() {
        val event =
            assertIs<EncryptionKeyRequestEvent>(
                request(
                    arrayOf(
                        arrayOf("P"),
                        arrayOf("P", "not-hex-and-too-short"),
                        arrayOf("P", "z".repeat(64)),
                        arrayOf("relay", ""),
                        arrayOf("relay", "not a relay"),
                        arrayOf("n", "abc"),
                    ),
                ),
            )
        assertNull(event.clientKey())
        assertNull(event.authorizationCode())
        assertEquals(emptyList(), event.responseRelays())
        assertNull(event.requestedEncryptionKey())
    }

    @Test
    fun requestHasNoGraphEdgesAndIsNotSearchable() {
        val event = request(arrayOf(arrayOf("P", requesterClient)))
        assertFalse(event is PubKeyHintProvider)
        assertFalse(event is EventHintProvider)
        assertFalse(event is AddressHintProvider)
        assertFalse(event is SearchableEvent)
    }

    @Test
    fun requestBuildRoundTrips() {
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!
        val event = EncryptionKeyRequestEvent.build(requesterClient, listOf(relay), encryptionPub).toEvent(identity)

        assertEquals(listOf("P", requesterClient), event.tags.first { it[0] == "P" }.toList())
        assertEquals(listOf("pubkey", requesterClient), event.tags.first { it[0] == "pubkey" }.toList())
        assertEquals(requesterClient, event.clientKey())
        assertTrue(event.hasConsistentClientKey())
        assertEquals(listOf(relay), event.responseRelays())
        assertEquals(encryptionPub, event.requestedEncryptionKey())

        val noAlias = EncryptionKeyRequestEvent.build(requesterClient, withPubKeyAlias = false).toEvent(identity)
        assertFalse(noAlias.tags.any { it[0] == "pubkey" })
        assertTrue(noAlias.tags.none { it[0] == "relay" || it[0] == "n" })
    }

    @Test
    fun transferEncryptsToTheRequesterAndOnlyItCanOpenIt() {
        val encryptionKey = KeyPair()
        val encryptionPrivHex = encryptionKey.privKey!!.toHexKey()
        val encryptionPubHex = encryptionKey.pubKey.toHexKey()
        val sender = KeyPair()
        val requester = KeyPair()
        val stranger = KeyPair()
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

        val event =
            EncryptionKeyTransferEvent
                .build(
                    encryptionPrivKey = encryptionPrivHex,
                    senderClientPrivKey = sender.privKey!!,
                    requesterClientPubKey = requester.pubKey.toHexKey(),
                    requesterRelayHint = relay,
                    identityPubKey = identity,
                ).toEvent(identity)

        assertIs<EncryptionKeyTransferEvent>(event)
        assertEquals(sender.pubKey.toHexKey(), event.senderClientKey())
        assertEquals(requester.pubKey.toHexKey(), event.requesterClientKey())
        assertEquals(listOf(identity, requester.pubKey.toHexKey()), event.recipients())
        assertNotEquals(encryptionPrivHex, event.content)

        assertEquals(encryptionPrivHex, event.decryptKey(requester.privKey!!))
        assertEquals(encryptionPrivHex, event.decryptAndVerify(requester.privKey!!, encryptionPubHex))
        // Wrong expected key: NIP-4E says to ignore non-matching responses.
        assertNull(event.decryptAndVerify(requester.privKey!!, "f".repeat(64)))
        // Anyone else: undecryptable, ignored.
        assertNull(event.decryptKey(stranger.privKey!!))

        // RECIPIENT edges are the `p` tags; only the one with a relay hint is a hint.
        assertEquals(listOf(identity, requester.pubKey.toHexKey()), event.linkedPubKeys())
        assertEquals(listOf(requester.pubKey.toHexKey()), event.pubKeyHints().map { it.pubkey })
    }

    @Test
    fun transferWithoutTheIdentityTagStillFindsTheRequester() {
        // Flotilla-style: only the requester's client key in `p`.
        val event =
            assertIs<EncryptionKeyTransferEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    identity,
                    1L,
                    EncryptionKeyTransferEvent.KIND,
                    arrayOf(arrayOf("P", "c".repeat(64)), arrayOf("p", requesterClient)),
                    "AnBz",
                    "",
                ),
            )
        assertEquals("c".repeat(64), event.senderClientKey())
        assertEquals(requesterClient, event.requesterClientKey())
    }

    @Test
    fun malformedTransfersReadAsNullNeverThrow() {
        val requester = KeyPair()
        val event =
            assertIs<EncryptionKeyTransferEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    identity,
                    1L,
                    EncryptionKeyTransferEvent.KIND,
                    arrayOf(arrayOf("P", "short"), arrayOf("p"), arrayOf("p", "xyz")),
                    "not-a-nip44-payload",
                    "",
                ),
            )
        assertNull(event.senderClientKey())
        assertNull(event.requesterClientKey())
        assertEquals(emptyList(), event.linkedPubKeys())
        assertNull(event.decryptKey(requester.privKey!!))

        val withSender =
            assertIs<EncryptionKeyTransferEvent>(
                EventFactory.create<Event>("1".repeat(64), identity, 1L, EncryptionKeyTransferEvent.KIND, arrayOf(arrayOf("P", KeyPair().pubKey.toHexKey())), "garbage", ""),
            )
        assertNull(withSender.decryptKey(requester.privKey!!))
        assertFalse(isSearchable(withSender))
    }
}
