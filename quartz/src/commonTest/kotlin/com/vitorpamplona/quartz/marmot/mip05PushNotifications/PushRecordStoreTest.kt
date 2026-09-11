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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `features/push-notifications.md`, "Record state".
 *
 * The store's whole job is convergence: two members who see the same records in
 * different orders must end up with the same active set. Every test here is
 * therefore about a decision the store must NOT make on arrival order, sender
 * identity, or array position.
 */
class PushRecordStoreTest {
    private val groupId = "000102030405060708090a0b0c0d0e0f"
    private val server = "2f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4"
    private val now = 1735680000000L

    private val alicePriv = ByteArray(32).also { it[31] = 3 }
    private val bobPriv = ByteArray(32).also { it[31] = 5 }
    private val alice = Nip01Crypto.pubKeyCreate(alicePriv).toHexKey()
    private val bob = Nip01Crypto.pubKeyCreate(bobPriv).toHexKey()

    private val everyone: (HexKey) -> Boolean = { it == alice || it == bob }

    private fun token(seed: Int) = ByteArray(PushSignedRecord.ENCRYPTED_TOKEN_BYTES) { ((it + seed) % 251).toByte() }

    private fun store(currentProfile: Boolean = true) = PushRecordStore(groupId, currentProfile)

    private fun entry(
        priv: ByteArray,
        ownerTs: Long,
        leafIndex: Int = 0,
        platform: PushPlatform = PushPlatform.APNS,
        relayHint: String = "",
        seed: Int = 0,
        fingerprint: String = "sha256:000102030405060708090a0b",
    ): PushTokenEntry {
        val member = Nip01Crypto.pubKeyCreate(priv).toHexKey()
        val encryptedToken = token(seed)
        val tags =
            PushOwnerProof.tags(
                PushRecordKind.TOKEN,
                groupId,
                member,
                leafIndex,
                platform.wireName,
                server,
                fingerprint,
                ownerTs,
                relayHint,
            )
        val content = PushBase64.encode(encryptedToken)
        val sig = Nip01Crypto.sign(PushOwnerProof.eventId(member, tags, content), priv)
        return PushTokenEntry(member, leafIndex, platform, fingerprint, server, relayHint, encryptedToken, ownerTs, sig)
    }

    private fun removal(
        priv: ByteArray,
        ownerTs: Long,
        leafIndex: Int = 0,
        platform: PushPlatform = PushPlatform.APNS,
        fingerprint: String = "sha256:000102030405060708090a0b",
    ): PushRemovalEntry {
        val member = Nip01Crypto.pubKeyCreate(priv).toHexKey()
        val tags =
            PushOwnerProof.tags(
                PushRecordKind.REMOVAL,
                groupId,
                member,
                leafIndex,
                platform.wireName,
                server,
                fingerprint,
                ownerTs,
                "",
            )
        val sig = Nip01Crypto.sign(PushOwnerProof.eventId(member, tags, ""), priv)
        return PushRemovalEntry(member, leafIndex, platform, fingerprint, server, ownerTs, sig)
    }

    @Test
    fun aVerifiedEntryBecomesTheActiveRecord() {
        val store = store()
        val e = entry(alicePriv, now)
        assertEquals(setOf(e.key), store.applyTokens(listOf(e), now, everyone))
        assertEquals(e, store.activeFor(e.key))
    }

    @Test
    fun anUnverifiableEntryIsDroppedAndNothingElseHappens() {
        val store = store()
        val forged = entry(alicePriv, now).copy(ownerSig = ByteArray(64))
        assertTrue(store.applyTokens(listOf(forged), now, everyone).isEmpty())
        assertNull(store.activeFor(forged.key))
        assertNull(store.stampFor(forged.key))
    }

    @Test
    fun anEntryFromANonMemberIsDroppedEvenThoughItVerifies() {
        val store = store()
        val e = entry(bobPriv, now)
        assertTrue(store.applyTokens(listOf(e), now) { it == alice }.isEmpty())
        assertNull(store.activeFor(e.key))
    }

    @Test
    fun aRelayedEntryIsAppliedRegardlessOfWhoCarriedIt() {
        // The whole point of owner authentication: alice can bring bob's record
        // to a member who has never been online at the same time as bob.
        val store = store()
        val bobs = entry(bobPriv, now)
        assertEquals(setOf(bobs.key), store.applyTokens(listOf(bobs), now, everyone))
        assertEquals(bobs, store.activeFor(bobs.key))
    }

    @Test
    fun theLatestStampWinsWhicheverOrderTheyArrive() {
        val older = entry(alicePriv, now, seed = 1)
        val newer = entry(alicePriv, now + 1000, seed = 2)

        val forwards = store().also { it.applyTokens(listOf(older, newer), now + 1000, everyone) }
        val backwards = store().also { it.applyTokens(listOf(newer, older), now + 1000, everyone) }

        assertEquals(newer, forwards.activeFor(newer.key))
        assertEquals(newer, backwards.activeFor(newer.key))
    }

    @Test
    fun anEqualStampTieBreaksOnTheDigestNotOnArrayPosition() {
        // Two devices of one account can stamp the same millisecond. Without the
        // digest tie-break, two readers would converge on different tokens and
        // neither would be wrong.
        val a = entry(alicePriv, now, seed = 7)
        val b = entry(alicePriv, now, seed = 9)
        val expected = if (a.stamp(groupId) > b.stamp(groupId)) a else b

        assertEquals(expected, store().also { it.applyTokens(listOf(a, b), now, everyone) }.activeFor(a.key))
        assertEquals(expected, store().also { it.applyTokens(listOf(b, a), now, everyone) }.activeFor(a.key))
    }

    @Test
    fun reApplyingTheSameSignedRecordIsANoOp() {
        val store = store()
        val e = entry(alicePriv, now)
        store.applyTokens(listOf(e), now, everyone)
        assertTrue(store.applyTokens(listOf(e), now, everyone).isEmpty())
        assertEquals(e, store.activeFor(e.key))
    }

    @Test
    fun aFarFutureStampIsRefused() {
        // owner_ts is a latest-wins high-water mark; a far-future signed stamp
        // would otherwise pin the record forever.
        val store = store()
        val e = entry(alicePriv, now + PushGossip.OWNER_TS_MAX_FUTURE_MILLIS + 1)
        assertTrue(store.applyTokens(listOf(e), now, everyone).isEmpty())
        // Right at the bound it is still accepted.
        val edge = entry(alicePriv, now + PushGossip.OWNER_TS_MAX_FUTURE_MILLIS)
        assertEquals(setOf(edge.key), store.applyTokens(listOf(edge), now, everyone))
    }

    @Test
    fun aRemovalDeletesAndLeavesATombstone() {
        val store = store()
        val e = entry(alicePriv, now)
        store.applyTokens(listOf(e), now, everyone)

        val r = removal(alicePriv, now + 1000)
        assertEquals(setOf(r.key), store.applyRemovals(listOf(r), now + 1000, everyone))
        assertNull(store.activeFor(r.key))
        assertTrue(store.isTombstoned(r.key))
    }

    @Test
    fun aTombstoneSuppressesAStaleListThatArrivesLater() {
        // The realistic race: a member assembled a kind 448 before the removal
        // and delivers it after. Arrival order must not resurrect the token.
        val store = store()
        val stale = entry(alicePriv, now)
        val r = removal(alicePriv, now + 1000)
        store.applyTokens(listOf(stale), now, everyone)
        store.applyRemovals(listOf(r), now + 1000, everyone)

        assertTrue(store.applyTokens(listOf(stale), now + 2000, everyone).isEmpty())
        assertNull(store.activeFor(stale.key))
        assertTrue(store.isTombstoned(stale.key))
    }

    @Test
    fun aNewerRegistrationClearsTheTombstone() {
        val store = store()
        val r = removal(alicePriv, now + 1000)
        store.applyRemovals(listOf(r), now + 1000, everyone)

        val fresh = entry(alicePriv, now + 2000, seed = 4)
        assertEquals(setOf(fresh.key), store.applyTokens(listOf(fresh), now + 2000, everyone))
        assertFalse(store.isTombstoned(fresh.key))
        assertEquals(fresh, store.activeFor(fresh.key))
    }

    @Test
    fun aStaleRemovalCannotRevokeANewerToken() {
        val store = store()
        val fresh = entry(alicePriv, now + 2000)
        store.applyTokens(listOf(fresh), now + 2000, everyone)

        val stale = removal(alicePriv, now)
        assertTrue(store.applyRemovals(listOf(stale), now + 2000, everyone).isEmpty())
        assertEquals(fresh, store.activeFor(fresh.key))
    }

    @Test
    fun aRemovalDoesNotTouchASiblingLeaf() {
        // leaf_index is in the record key precisely so one device cannot revoke
        // another device's live token.
        val store = store()
        val leafZero = entry(alicePriv, now, leafIndex = 0)
        val leafOne = entry(alicePriv, now, leafIndex = 1)
        store.applyTokens(listOf(leafZero, leafOne), now, everyone)

        store.applyRemovals(listOf(removal(alicePriv, now + 1000, leafIndex = 0)), now + 1000, everyone)
        assertNull(store.activeFor(leafZero.key))
        assertEquals(leafOne, store.activeFor(leafOne.key))
    }

    @Test
    fun oneAccountKeepsSeparateRecordsPerPlatformAndServer() {
        val store = store()
        val apns = entry(alicePriv, now, platform = PushPlatform.APNS)
        val fcm = entry(alicePriv, now, platform = PushPlatform.FCM)
        store.applyTokens(listOf(apns, fcm), now, everyone)
        assertEquals(2, store.active().size)
    }

    @Test
    fun aRemovedLeafLosesItsRecordItsStampAndItsTombstone() {
        val store = store()
        val leafZero = entry(alicePriv, now, leafIndex = 0)
        val leafOne = entry(alicePriv, now, leafIndex = 1)
        store.applyTokens(listOf(leafZero, leafOne), now, everyone)
        store.applyRemovals(listOf(removal(alicePriv, now + 1000, leafIndex = 0)), now + 1000, everyone)

        store.forgetLeaf(alice, 0)
        assertNull(store.stampFor(leafZero.key))
        assertFalse(store.isTombstoned(leafZero.key))
        // The sibling leaf is a different key and a still-current member.
        assertEquals(leafOne, store.activeFor(leafOne.key))
        assertNotNull(store.stampFor(leafOne.key))
    }

    @Test
    fun aRestoredStoreStillRefusesAStaleRelay() {
        // A tombstone is durable or it is worthless: it is the only high-water
        // mark stopping a relayed record from resurrecting a revoked token, and
        // a relayed record's carrying epoch is unbounded.
        val first = store()
        val stale = entry(alicePriv, now)
        first.applyTokens(listOf(stale), now, everyone)
        first.applyRemovals(listOf(removal(alicePriv, now + 1000)), now + 1000, everyone)

        val restarted = store()
        restarted.restore(first.active(), first.snapshotStamps(), first.snapshotTombstones())

        assertTrue(restarted.applyTokens(listOf(stale), now + 5000, everyone).isEmpty())
        assertNull(restarted.activeFor(stale.key))
    }
}
