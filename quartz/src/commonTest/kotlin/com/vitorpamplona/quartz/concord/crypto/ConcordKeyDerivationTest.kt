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
package com.vitorpamplona.quartz.concord.crypto

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConcordKeyDerivationTest {
    private val secretA = ByteArray(32) { 1 }
    private val secretB = ByteArray(32) { 2 }
    private val idA = ByteArray(32) { 0x11 }
    private val idB = ByteArray(32) { 0x22 }

    // ---- buildInfo layout -----------------------------------------------------

    @Test
    fun buildInfoLayoutWithIdAndEpoch() {
        val label = ConcordLabels.CHANNEL // "concord/channel" (15 bytes)
        val info = ConcordKeyDerivation.buildInfo(label, idA, epoch = 1)

        // utf8(label) || 0x00 || id[32] || epoch_be8  = 15 + 1 + 32 + 8 = 56
        assertEquals(15 + 1 + 32 + 8, info.size)
        assertContentEquals(label.encodeToByteArray(), info.copyOfRange(0, 15))
        assertEquals(0x00.toByte(), info[15])
        assertContentEquals(idA, info.copyOfRange(16, 48))
        // epoch 1 big-endian
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1), info.copyOfRange(48, 56))
    }

    @Test
    fun buildInfoOmitsEpochWhenNull() {
        val info = ConcordKeyDerivation.buildInfo(ConcordLabels.VOICE_SENDER, idA, epoch = null)
        // label + 0x00 + id, no epoch tail
        assertEquals(ConcordLabels.VOICE_SENDER.encodeToByteArray().size + 1 + 32, info.size)
    }

    @Test
    fun buildInfoOmitsIdWhenNull() {
        val info = ConcordKeyDerivation.buildInfo(ConcordLabels.CONTROL, id = null, epoch = 5)
        // label + 0x00 + epoch_be8
        assertEquals(ConcordLabels.CONTROL.encodeToByteArray().size + 1 + 8, info.size)
    }

    // ---- CORD-05 invite bundle key --------------------------------------------

    /**
     * The invite-key HKDF `info` MUST carry the 32-byte all-zero id (CORD-05 A.1: "the id is
     * always present, 32 bytes, all-zeroes where a label has no meaningful id" — A.6 lists
     * `concord/invite-key` with `id = 0…0`). Omitting it derived a key that could not open a
     * reference-client (Armada) bundle — confirmed by decrypting a live Soapbox invite: only the
     * zero-id key produced valid JSON. This pins the id in so the derivation can't silently regress.
     */
    @Test
    fun inviteBundleKeyIncludesZeroId() {
        val token = ByteArray(16) { it.toByte() }
        val zeroId = ConcordKeyDerivation.hkdf32(token, ConcordKeyDerivation.buildInfo(ConcordLabels.INVITE_KEY, ByteArray(32)))
        val idLess = ConcordKeyDerivation.hkdf32(token, ConcordKeyDerivation.buildInfo(ConcordLabels.INVITE_KEY))
        assertContentEquals(zeroId, ConcordKeyDerivation.inviteBundleKey(token))
        assertNotEquals(zeroId.toHexKey(), idLess.toHexKey())
    }

    // ---- groupKey -------------------------------------------------------------

    @Test
    fun groupKeyIsDeterministic() {
        val a = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 0)
        val b = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 0)
        assertContentEquals(a.secretKey, b.secretKey)
        assertContentEquals(a.publicKey, b.publicKey)
        assertContentEquals(a.conversationKey, b.conversationKey)
    }

    @Test
    fun groupKeyProducesValidXOnlyPubkey() {
        val gk = ConcordKeyDerivation.groupKey(ConcordLabels.CONTROL, secretA, idA, 0)
        assertEquals(32, gk.publicKey.size)
        assertTrue(Secp256k1Instance.isPrivateKeyValid(gk.secretKey))
        // pk must be the x-only pubkey of sk
        assertContentEquals(KeyPair(privKey = gk.secretKey).pubKey, gk.publicKey)
    }

    @Test
    fun groupKeyRotatesWithEpoch() {
        val e0 = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 0)
        val e1 = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 1)
        assertNotEquals(e0.publicKeyHex, e1.publicKeyHex)
    }

    @Test
    fun groupKeyIsDistinctAcrossLabelsIdsAndSecrets() {
        val base = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 0).publicKeyHex
        val byLabel = ConcordKeyDerivation.groupKey(ConcordLabels.CONTROL, secretA, idA, 0).publicKeyHex
        val byId = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idB, 0).publicKeyHex
        val bySecret = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretB, idA, 0).publicKeyHex
        assertNotEquals(base, byLabel)
        assertNotEquals(base, byId)
        assertNotEquals(base, bySecret)
    }

    @Test
    fun groupKeyConversationKeyRoundTripsSelfEcdh() {
        val gk = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 3)
        // conversation key is self-ECDH of sk against its own pk
        assertContentEquals(Nip44.v2.getConversationKey(gk.secretKey, gk.publicKey), gk.conversationKey)

        val payload = Nip44.v2.encrypt("gm chat", gk.conversationKey).encodePayload()
        assertEquals("gm chat", Nip44.v2.decrypt(payload, gk.conversationKey))
    }

    // ---- communityId ----------------------------------------------------------

    @Test
    fun communityIdIsDeterministicAndOwnerBound() {
        val owner = KeyPair()
        val salt = ConcordKeyDerivation.newOwnerSalt()
        val id1 = ConcordKeyDerivation.communityId(owner.pubKey, salt)
        val id2 = ConcordKeyDerivation.communityId(owner.pubKey, salt)
        assertContentEquals(id1, id2)
        assertEquals(32, id1.size)

        // Different salt or different owner ⇒ different id (multiple communities per owner)
        val otherSalt = ConcordKeyDerivation.newOwnerSalt()
        assertNotEquals(id1.toHexKey(), ConcordKeyDerivation.communityId(owner.pubKey, otherSalt).toHexKey())
        assertNotEquals(id1.toHexKey(), ConcordKeyDerivation.communityId(KeyPair().pubKey, salt).toHexKey())
    }

    // ---- voice keys -----------------------------------------------------------

    @Test
    fun voiceKeysRideEpochAndDifferFromChatKeys() {
        val chat = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secretA, idA, 0).publicKeyHex
        val voiceSigner0 = ConcordKeyDerivation.voiceSignerKey(secretA, idA, 0).publicKeyHex
        val voiceSigner1 = ConcordKeyDerivation.voiceSignerKey(secretA, idA, 1).publicKeyHex
        assertNotEquals(chat, voiceSigner0)
        assertNotEquals(voiceSigner0, voiceSigner1)

        val media = ConcordKeyDerivation.voiceMediaKey(secretA, idA, 0)
        assertEquals(32, media.size)
        val alice = ConcordKeyDerivation.voiceSenderKey(media, "alice")
        val bob = ConcordKeyDerivation.voiceSenderKey(media, "bob")
        assertEquals(32, alice.size)
        assertNotEquals(alice.toHexKey(), bob.toHexKey())
        // deterministic per identity
        assertContentEquals(alice, ConcordKeyDerivation.voiceSenderKey(media, "alice"))
    }

    // ---- rekey locator --------------------------------------------------------

    @Test
    fun recipientLocatorIsDeterministicAndDirectionalAndEpochBound() {
        val rotator = KeyPair().pubKey
        val recipient = KeyPair().pubKey
        val loc0 = ConcordKeyDerivation.recipientLocator(rotator, recipient, idA, 1)
        assertEquals(32, loc0.size)
        assertContentEquals(loc0, ConcordKeyDerivation.recipientLocator(rotator, recipient, idA, 1))

        // direction matters (rotator‖recipient vs recipient‖rotator)
        assertNotEquals(loc0.toHexKey(), ConcordKeyDerivation.recipientLocator(recipient, rotator, idA, 1).toHexKey())
        // epoch rotates the locator
        assertNotEquals(loc0.toHexKey(), ConcordKeyDerivation.recipientLocator(rotator, recipient, idA, 2).toHexKey())
    }
}
