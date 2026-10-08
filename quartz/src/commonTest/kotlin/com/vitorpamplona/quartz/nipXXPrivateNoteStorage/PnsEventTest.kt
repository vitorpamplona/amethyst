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
package com.vitorpamplona.quartz.nipXXPrivateNoteStorage

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip59Giftwrap.HasInnerEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PnsEventTest {
    // Device secret 0x00..02, the key of nostrdb's `test_pns_unwrap` (damus-io/nostrdb test.c)
    // and nostrdb-rs' `test_matches_nostrdb_c_test_vector` (nostrdb_net/src/pns.rs).
    private val deviceKey = "0000000000000000000000000000000000000000000000000000000000000002".hexToByteArray()
    private val keys = PnsKeys.derive(deviceKey)

    // Kind-1080 event from nostrdb's `test_pns_unwrap`, produced by an independent implementation
    // (nostrdb's C ingester decrypts it to a kind-1 rumor with content "hello from pns").
    private val nostrdbVector =
        """{"id":"bfcd0d415ea1b4772d075de9e8f98ac5c2224a9d2512a68c984c39e594b46b15","pubkey":"fa22d53e9d38ca7af1e66dcf88f5fb2444368df6bd16580b5827c8cfbc622d4e","created_at":1700000000,"kind":1080,"tags":[],"content":"Ahtvtc5B/m/6n//vdOxtxR/+UbWx5qDP/teNxr563idfhMEObQ9v3Z1UI0HSyHWHCq2a9zwehpBYrJPXEnyvrzHeJQTQuz3AOKfJA/FT6MSMsGAyi197YDP3YaJfkDcdY0Aqnx5kXpir5IC95LCXjyPwDWms3ndJM3XksPLY0+mG8cYxdPkLxgnpqzs9N1pjf2ecPyvd8vx+3DVEY2APPalYE9L+rCYE5UyZzzDR2YD3MPF7wrb++wGeSL+46rvy2J/ZmUEnbXkC288MxTT77nSroiSB46PpcvbxBBqD82Q+I+G3Q3KWg16hn81MV2faXZ3rajZrZrXM+gs8kVunTGkH86KpAgt22RqdDcQiiogJoSL4k5cKzMGo268R+efqjZLt","sig":"54f7ac921abdda5f14c045c2e3ba6f5cac1a41d8d46ffe370f1473f2b2dac938dc2801c818dc6916c6f1912a56c4ad327ed77f74a4acd85cf9ef209626a55616"}"""

    /** Seals arbitrary plaintext as a PNS envelope of [keys], bypassing [PnsEvent.create]'s checks. */
    private fun envelopeOf(
        plaintext: String,
        with: PnsKeys = keys,
        createdAt: Long = 1_700_000_100,
    ): PnsEvent = with.signer.sign(PnsEvent.build(with.encrypt(plaintext), createdAt))

    // ---- registries ----

    @Test
    fun kindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(PnsEvent.KIND))
        assertIs<PnsEvent>(EventFactory.create<Event>("a", "b", 0, 1080, emptyArray(), "", "c"))
    }

    @Test
    fun isNotSearchableAndHasNoHintProviders() {
        val event = Event.fromJson(nostrdbVector)
        assertFalse(event is SearchableEvent)
        assertTrue(event is HasInnerEvent)
        assertTrue(event.isContentEncoded())
    }

    // ---- key derivation ----

    @Test
    fun derivationMatchesIndependentVectors() {
        // Device pubkey of secret 2 (also asserted by nostrdb's test).
        assertEquals("c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5", keys.devicePubKey)
        // pns_key and pns_nip44_key computed independently with Python's stdlib:
        //   pk = hmac.new(b"nip-pns", bytes(31) + b"\x02", hashlib.sha256).digest()
        //   nk = hmac.new(b"nip44-v2", pk, hashlib.sha256).digest()
        assertEquals("a847c33e3a22ad342c61c43fc1e7198dffc0b081ee330743538ff3ee236a9986", keys.keyPair.privKey!!.toHexKey())
        assertEquals("ab844813e5240b46342ddc4bb1fc065a49fea5ebcf826b10c65e7aa9fc1fb08d", keys.nip44Key.toHexKey())
        // PNS pubkey asserted by nostrdb (C) and nostrdb-rs (Rust).
        assertEquals("fa22d53e9d38ca7af1e66dcf88f5fb2444368df6bd16580b5827c8cfbc622d4e", keys.pubKey)
    }

    @Test
    fun derivationIsDeterministicAndUnlinkable() {
        val again = PnsKeys.derive(deviceKey.copyOf())
        assertEquals(keys.pubKey, again.pubKey)
        assertContentEquals(keys.nip44Key, again.nip44Key)
        assertNotEquals(keys.devicePubKey, keys.pubKey)
        assertNotEquals(keys.pubKey, PnsKeys.derive(KeyPair().privKey!!).pubKey)
    }

    @Test
    fun derivesOnlyFromLocalKeys() {
        val device = KeyPair(privKey = deviceKey)
        assertEquals(keys.pubKey, PnsKeys.deriveOrNull(device)?.pubKey)
        assertEquals(keys.pubKey, PnsKeys.fromSignerOrNull(NostrSignerInternal(device))?.pubKey)
        // Read-only accounts have no secret to run the HKDF over.
        assertNull(PnsKeys.deriveOrNull(KeyPair(pubKey = device.pubKey)))
        assertNull(PnsKeys.fromSignerOrNull(NostrSignerInternal(KeyPair(pubKey = device.pubKey))))
        assertFailsWith<IllegalArgumentException> { PnsKeys.derive(ByteArray(31)) }
    }

    // ---- interop ----

    @Test
    fun decryptsTheNostrdbVector() {
        val envelope = Event.fromJson(nostrdbVector)
        assertIs<PnsEvent>(envelope)
        assertTrue(envelope.verify())
        assertTrue(envelope.isFrom(keys))

        val inner = envelope.decryptThrowing(keys)
        assertIs<TextNoteEvent>(inner)
        assertEquals(1, inner.kind)
        assertEquals("hello from pns", inner.content)
        assertEquals(keys.devicePubKey, inner.pubKey)
        assertEquals("", inner.sig)
        assertEquals(EventHasher.hashId(inner.pubKey, inner.createdAt, inner.kind, inner.tags, inner.content), inner.id)
        assertEquals(inner.id, envelope.innerEventId)
        assertEquals(inner.id, envelope.copyNoContent().innerEventId)
        assertEquals("", envelope.copyNoContent().content)
    }

    // ---- build / read round trips ----

    @Test
    fun rumorRoundTrip() {
        val template = TextNoteEvent.build("my diary", createdAt = 1_700_000_000)
        val envelope = PnsEvent.createFromTemplate(template, keys, createdAt = 1_700_000_500)

        assertEquals(PnsEvent.KIND, envelope.kind)
        assertEquals(keys.pubKey, envelope.pubKey)
        assertEquals(1_700_000_500, envelope.createdAt)
        assertEquals(0, envelope.tags.size)
        assertTrue(envelope.verify())

        // The ciphertext carries no sig: the rumor really is unsigned on the wire.
        val plaintext = keys.decrypt(envelope.content)
        assertFalse(plaintext.contains("\"sig\""))

        val parsed = assertIs<PnsEvent>(Event.fromJson(envelope.toJson()))
        val inner = parsed.decryptThrowing(keys)
        assertIs<TextNoteEvent>(inner)
        assertEquals("my diary", inner.content)
        assertEquals(1_700_000_000, inner.createdAt)
        assertEquals(keys.devicePubKey, inner.pubKey)
        assertEquals(PnsEvent.assembleRumor(template, keys).id, inner.id)
    }

    @Test
    fun signedInnerEventRoundTripAnyAuthor() {
        val other = NostrSignerSync(KeyPair())
        val signed = other.sign<TextNoteEvent>(TextNoteEvent.build("signed elsewhere", createdAt = 1_700_000_000))

        val inner = PnsEvent.create(signed, keys).decryptThrowing(keys)

        assertEquals(signed.id, inner.id)
        assertEquals(signed.sig, inner.sig)
        assertEquals(signed.pubKey, inner.pubKey)
        assertTrue(inner.verify())
    }

    @Test
    fun encryptionUsesAFreshNonce() {
        val rumor = PnsEvent.assembleRumor(TextNoteEvent.build("same", createdAt = 1), keys)
        assertNotEquals(PnsEvent.create(rumor, keys).content, PnsEvent.create(rumor, keys).content)
    }

    // ---- validation ----

    @Test
    fun refusesToBuildARumorOfAnotherAuthor() {
        val foreign = PnsEvent.assembleRumor(TextNoteEvent.build("x"), PnsKeys.derive(KeyPair().privKey!!))
        assertFailsWith<IllegalArgumentException> { PnsEvent.create(foreign, keys) }
    }

    @Test
    fun rejectsARumorClaimingAnotherAuthor() {
        val foreignAuthor = KeyPair().pubKey.toHexKey()
        val rumor = Rumor(null, foreignAuthor, 1, 1, emptyArray(), "impostor")
        val envelope = envelopeOf(Rumor.toJson(rumor))

        assertNull(envelope.decryptOrNull(keys))
        assertNull(envelope.innerEventId)
    }

    @Test
    fun rejectsASignedInnerEventThatDoesNotVerify() {
        val signed = NostrSignerSync(KeyPair()).sign<TextNoteEvent>(TextNoteEvent.build("original", createdAt = 1))
        val tampered = TextNoteEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, "tampered", signed.sig)

        assertNull(envelopeOf(tampered.toJson()).decryptOrNull(keys))
        assertNotNull(envelopeOf(signed.toJson()).decryptOrNull(keys))
    }

    @Test
    fun fillsAMissingAuthorAndCreatedAtAndRecomputesTheId() {
        val bogusId = "f".repeat(64)
        val envelope = envelopeOf("""{"id":"$bogusId","kind":1,"tags":[["t","x"]],"content":"no author"}""", createdAt = 1_700_000_123)

        val inner = envelope.decryptThrowing(keys)
        assertEquals(keys.devicePubKey, inner.pubKey)
        assertEquals(1_700_000_123, inner.createdAt)
        assertEquals("no author", inner.content)
        assertNotEquals(bogusId, inner.id)
        assertEquals(EventHasher.hashId(inner.pubKey, inner.createdAt, inner.kind, inner.tags, inner.content), inner.id)
    }

    @Test
    fun rejectsARumorWithoutKind() {
        assertNull(envelopeOf("""{"pubkey":"${keys.devicePubKey}","created_at":1,"tags":[],"content":"x"}""").decryptOrNull(keys))
    }

    @Test
    fun malformedPayloadsReturnNullInsteadOfThrowing() {
        // Plaintext that is not an event.
        assertNull(envelopeOf("not json").decryptOrNull(keys))
        assertNull(envelopeOf("[1,2,3]").decryptOrNull(keys))
        // Content that is not a NIP-44 payload.
        assertNull(keys.signer.sign<PnsEvent>(PnsEvent.build("garbage")).decryptOrNull(keys))
        assertNull(keys.signer.sign<PnsEvent>(PnsEvent.build("")).decryptOrNull(keys))
    }

    @Test
    fun anotherDevicesKeysCannotRead() {
        val otherKeys = PnsKeys.derive(KeyPair().privKey!!)
        val envelope = PnsEvent.createFromTemplate(TextNoteEvent.build("mine"), keys)

        assertFalse(envelope.isFrom(otherKeys))
        assertNull(envelope.decryptOrNull(otherKeys))

        // Even with a matching author check bypassed, the other conversation key fails the MAC.
        assertFailsWith<IllegalStateException> { otherKeys.decrypt(envelope.content) }

        // An envelope signed by someone else is refused before decrypting.
        val stranger = NostrSignerSync(KeyPair()).sign<PnsEvent>(PnsEvent.build(envelope.content))
        assertNull(stranger.decryptOrNull(keys))
    }
}
