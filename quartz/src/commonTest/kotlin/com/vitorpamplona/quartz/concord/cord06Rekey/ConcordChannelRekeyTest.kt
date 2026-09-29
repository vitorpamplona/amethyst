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
package com.vitorpamplona.quartz.concord.cord06Rekey

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ConcordLabels
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * CORD-06 §1-2 single-channel Rekeys: the channel rekey address, the 72-byte scope-bound blob,
 * the `prevcommit` continuity walk, chunk completeness, racing rotators and the removal rule —
 * pinned to the reference client's `lib/rekey.ts` / `useChannelRekeyWatch`.
 */
class ConcordChannelRekeyTest {
    private val admin = NostrSignerInternal(KeyPair())
    private val alice = NostrSignerInternal(KeyPair())
    private val bob = NostrSignerInternal(KeyPair())
    private val root = ByteArray(32) { 0x21 }
    private val channelId = ByteArray(32) { 0x5C }
    private val channelHex = channelId.toHexKey()
    private val key0 = ByteArray(32) { 0x10 }
    private val now = 1_700_000_000L

    private fun keysFor(vararg epochs: Long): Map<HexKey, GroupKey> = epochs.associate { e -> ConcordChannelRekey.address(root, channelId, e).let { it.publicKeyHex to it } }

    private suspend fun rotate(
        heldKey: ByteArray,
        heldEpoch: Long,
        newKey: ByteArray,
        recipients: List<HexKey>,
        rotator: NostrSignerInternal = admin,
        createdAt: Long = now,
    ): List<Event> = ConcordChannelRekey.build(rotator, root, channelId, heldKey, heldEpoch, newKey, recipients + rotator.pubKey, createdAt)

    private suspend fun walk(
        wraps: List<Event>,
        me: NostrSignerInternal,
        heldKey: ByteArray = key0,
        heldEpoch: Long = 0,
        joinedAt: Long = 0,
        honored: (ChannelRotation) -> Boolean = { true },
        outranksMe: (HexKey) -> Boolean = { true },
    ): ChannelRekeyOutcome {
        val keys = keysFor(*(heldEpoch + 1..heldEpoch + ConcordChannelRekey.LOOKAHEAD).toList().toLongArray())
        return ConcordChannelRekey.walk(ConcordChannelRekey.rotations(wraps, keys, channelHex), channelHex, heldKey, heldEpoch, me, joinedAt, honored, outranksMe)
    }

    @Test
    fun theChannelRekeyAddressIsTheRootKeyedRekeyPseudonym() {
        // CORD-02 derivation table: concord/rekey-pseudonym, prior community_root, channel_id, new_epoch.
        val address = ConcordChannelRekey.address(root, channelId, 3)
        assertEquals(ConcordKeyDerivation.groupKey(ConcordLabels.REKEY_PSEUDONYM, root, channelId, 3).publicKeyHex, address.publicKeyHex)
        // Keyed by the ROOT, never the channel key: every member can precompute it.
        assertNotEquals(ConcordKeyDerivation.groupKey(ConcordLabels.REKEY_PSEUDONYM, key0, channelId, 3).publicKeyHex, address.publicKeyHex)
        // Distinct per epoch and from the base-rotation address.
        assertNotEquals(address.publicKeyHex, ConcordChannelRekey.address(root, channelId, 4).publicKeyHex)
        assertNotEquals(address.publicKeyHex, ConcordKeyDerivation.baseRekeyAddress(root, channelId, 3).publicKeyHex)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun aChannelRotationCarriesTheSpecTagsAnd72ByteScopeBoundBlobs() =
        runTest {
            val newKey = ByteArray(32) { 0x77 }
            val wraps = rotate(key0, 0, newKey, listOf(alice.pubKey))
            assertEquals(1, wraps.size)
            val opened = assertNotNull(ConcordStreamEnvelope.openOrNull(wraps.single(), ConcordChannelRekey.address(root, channelId, 1)))
            // A rekey seal is encrypted; the seal names the rotator.
            assertEquals(ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED, opened.sealKind)
            assertEquals(admin.pubKey, opened.author)
            val rumor = opened.rumor
            assertEquals(ConcordRekey.KIND, rumor.kind)
            // ["scope", channel_id] ["newepoch", held+1] ["prevepoch", held] ["prevcommit", A.5 over the held key] ["chunk","1","1"]
            assertEquals(
                listOf(
                    listOf("scope", channelHex),
                    listOf("newepoch", "1"),
                    listOf("prevepoch", "0"),
                    listOf("prevcommit", ConcordKeyDerivation.epochKeyCommitment(0, key0).toHexKey()),
                    listOf("chunk", "1", "1"),
                ),
                rumor.tags.map { it.toList() },
            )
            // Alice's blob opens under the admin<->alice pairwise key to exactly scope ‖ epoch_be ‖ key.
            val blobs = ConcordRekey.decodeContent(rumor.content)
            assertEquals(2, blobs.size)
            val locator = ConcordKeyDerivation.recipientLocator(admin.pubKey.hexToByteArray(), alice.pubKey.hexToByteArray(), channelId, 1).toHexKey()
            val mine = blobs.single { it.locator == locator }
            val plain = Base64.Default.decode(Nip44.v2.decrypt(mine.wrapped, Nip44.v2.getConversationKey(alice.keyPair.privKey!!, admin.pubKey.hexToByteArray())))
            assertEquals(72, plain.size)
            assertContentEquals(channelId, plain.copyOfRange(0, 32))
            assertContentEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1), plain.copyOfRange(32, 40))
            assertContentEquals(newKey, plain.copyOfRange(40, 72))
        }

    @Test
    fun aKeptMemberAdoptsTheNewKeyAndARemovedOneIsCut() =
        runTest {
            val newKey = ByteArray(32) { 0x42 }
            val wraps = rotate(key0, 0, newKey, listOf(alice.pubKey))

            val kept = assertIs<ChannelRekeyOutcome.Adopted>(walk(wraps, alice))
            assertContentEquals(newKey, kept.key)
            assertEquals(1, kept.epoch)
            assertEquals(1, kept.steppedOver.size)
            assertContentEquals(key0, kept.steppedOver.single().key)

            val cut = assertIs<ChannelRekeyOutcome.Removed>(walk(wraps, bob))
            assertEquals(1, cut.epoch)
        }

    @Test
    fun noBlobIsARemovalOnlyFromAnOutrankingRotatorAfterTheJoin() =
        runTest {
            val wraps = rotate(key0, 0, ByteArray(32) { 0x42 }, listOf(alice.pubKey))
            // A rotator that does not strictly outrank us cannot cut us (CORD-06 Authority).
            assertSame(ChannelRekeyOutcome.None, walk(wraps, bob, outranksMe = { false }))
            // A rotation that predates our join is history, not an exclusion.
            assertSame(ChannelRekeyOutcome.None, walk(wraps, bob, joinedAt = now + 1))
            // An unauthorized rotator is ignored entirely.
            assertSame(ChannelRekeyOutcome.None, walk(wraps, alice, honored = { false }))
        }

    @Test
    fun aRotationOffAKeyWeDoNotHoldIsNeitherAdoptedNorARemoval() =
        runTest {
            // The rotator claims to extend a different key at our epoch: a fork, never adopted.
            val forged = rotate(ByteArray(32) { 0x66 }, 0, ByteArray(32) { 0x42 }, listOf(alice.pubKey))
            assertSame(ChannelRekeyOutcome.None, walk(forged, alice))
        }

    @Test
    fun aMissedRotationIsWalkedInOnePass() =
        runTest {
            val key1 = ByteArray(32) { 0x31 }
            val key2 = ByteArray(32) { 0x32 }
            val wraps = rotate(key0, 0, key1, listOf(alice.pubKey)) + rotate(key1, 1, key2, listOf(alice.pubKey), createdAt = now + 10)
            val adopted = assertIs<ChannelRekeyOutcome.Adopted>(walk(wraps, alice))
            assertContentEquals(key2, adopted.key)
            assertEquals(2, adopted.epoch)
            assertEquals(listOf(1L, 0L), adopted.steppedOver.map { it.epoch })
        }

    @Test
    fun aReadmissionAboveTheCutWinsAndACutAboveTheKeyWins() =
        runTest {
            val key1 = ByteArray(32) { 0x31 }
            val key2 = ByteArray(32) { 0x32 }
            // Cut at 1, re-admitted at 2 — but 2 extends key1, which bob never got: no adoption, cut stands.
            val wraps = rotate(key0, 0, key1, listOf(alice.pubKey)) + rotate(key1, 1, key2, listOf(alice.pubKey, bob.pubKey))
            assertIs<ChannelRekeyOutcome.Removed>(walk(wraps, bob))
            // Alice kept through 1 and then cut at 2: the cut above her key wins.
            val cutLater = rotate(key0, 0, key1, listOf(alice.pubKey)) + rotate(key1, 1, key2, emptyList())
            assertEquals(2, assertIs<ChannelRekeyOutcome.Removed>(walk(cutLater, alice)).epoch)
        }

    @Test
    fun racingRotatorsConvergeOnTheLowestKey() =
        runTest {
            val low = ByteArray(32) { 0x01 }
            val high = ByteArray(32) { 0x7F }
            val other = NostrSignerInternal(KeyPair())
            val wraps = rotate(key0, 0, high, listOf(alice.pubKey)) + rotate(key0, 0, low, listOf(alice.pubKey), rotator = other)
            val rotations = ConcordChannelRekey.rotations(wraps, keysFor(1), channelHex)
            // Two Rotators at one epoch never merge into one set.
            assertEquals(2, rotations.size)
            assertContentEquals(low, assertIs<ChannelRekeyOutcome.Adopted>(walk(wraps, alice)).key)
        }

    @Test
    fun anIncompleteRotationIsNeverARemoval() =
        runTest {
            // 130 recipients span two chunks; drop the second.
            val crowd = List(130) { KeyPair().pubKey.toHexKey() }
            val wraps = rotate(key0, 0, ByteArray(32) { 0x42 }, crowd)
            assertEquals(2, wraps.size)
            val rotations = ConcordChannelRekey.rotations(wraps.take(1), keysFor(1), channelHex)
            assertTrue(rotations.none { it.complete })
            assertSame(ChannelRekeyOutcome.None, walk(wraps.take(1), bob))
            assertIs<ChannelRekeyOutcome.Removed>(walk(wraps, bob))
        }

    @Test
    fun aRotationForAnotherChannelOrAtAnUnwatchedAddressIsIgnored() =
        runTest {
            val otherChannel = ByteArray(32) { 0x5D }
            val elsewhere = ConcordChannelRekey.build(admin, root, otherChannel, key0, 0, ByteArray(32) { 0x42 }, listOf(alice.pubKey), now)
            // Not at our channel's addresses, and its scope names another channel.
            assertTrue(ConcordChannelRekey.rotations(elsewhere, keysFor(1), channelHex).isEmpty())
            val atOurAddress = mapOf(ConcordChannelRekey.address(root, otherChannel, 1).let { it.publicKeyHex to it })
            assertTrue(ConcordChannelRekey.rotations(elsewhere, atOurAddress, channelHex).isEmpty())
        }
}
