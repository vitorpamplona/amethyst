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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.InMemoryPushStateStore
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushBase64
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushGossip
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushOwnerProof
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushPlatform
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushRecordKind
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushSignedRecord
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushTokenEntry
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenEncryption
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenListEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRemovalEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Push token gossip through the app layer
 * (`features/push-notifications.md`).
 *
 * The interesting failures here are not parse errors — those are covered in
 * quartz — but the ones where the app layer would quietly do the wrong thing:
 * announce a record no peer can verify, answer a request with nothing, or
 * forget a tombstone across a restart and start waking a revoked device again.
 */
class MarmotPushCoordinatorTest {
    private val nostrGroupId = "d".repeat(64)
    private val server = "2f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4"
    private val deviceToken = "a-real-looking-apns-token".encodeToByteArray()

    private class Fixture {
        val signer = NostrSignerInternal(KeyPair())
        val mlsStore = SnapshotStateStore()
        val messageStore = SnapshotMessageStore()
        val stateStore = InMemoryPushStateStore()
        val manager = MarmotManager(signer, mlsStore, messageStore, SnapshotBundleStore(), publisher = ACCEPTING_RELAY)
        val push = MarmotPushCoordinator(manager, stateStore)
    }

    private suspend fun Fixture.createGroup() =
        manager.createGroup(
            nostrGroupId,
            MarmotGroupData(nostrGroupId = nostrGroupId, name = "push", relays = listOf("wss://relay.invalid")),
        )

    private suspend fun Fixture.selfUpdate(
        ownerTs: Long = 1_735_680_000_000L,
        relayHint: String = "",
    ): Event =
        assertNotNull(
            push.buildSelfUpdate(nostrGroupId, PushPlatform.APNS, deviceToken, server, relayHint, ownerTs),
            "the group's own member should be able to announce a token",
        )

    @Test
    fun `a self update announces a record every peer can verify`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val event = f.selfUpdate(relayHint = "wss://push.example.com")

            assertEquals(TokenRequestEvent.KIND, event.kind)
            // Unsigned: it is an inner Marmot app payload, and its authority is
            // the entry's own owner_sig rather than a Nostr signature.
            assertEquals("", event.sig)

            val entry = PushGossip.decodeTokens(event.content).single()
            assertEquals(f.signer.pubKey, entry.memberIdHex)
            assertEquals(PushPlatform.APNS, entry.platform)
            assertEquals("wss://push.example.com", entry.relayHint)
            assertEquals(PushSignedRecord.fingerprintOf(PushPlatform.APNS, deviceToken), entry.tokenFingerprint)
            assertEquals(PushSignedRecord.ENCRYPTED_TOKEN_BYTES, entry.encryptedToken.size)

            // The proof is what a peer actually checks, and it binds the group.
            val groupIdHex = assertNotNull(f.manager.mlsGroupIdHex(nostrGroupId))
            assertTrue(entry.verifyOwner(groupIdHex, currentProfileGroup = false))
            assertFalse(entry.verifyOwner("00".repeat(16), currentProfileGroup = false))
        }

    @Test
    fun `the announced record is applied locally so a later list carries it`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.selfUpdate()

            val list = assertNotNull(f.push.buildTokenList(nostrGroupId))
            assertEquals(TokenListEvent.KIND, list.kind)
            assertEquals(1, PushGossip.decodeTokens(list.content).size)
        }

    @Test
    fun `there is no list response when we hold nothing`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            // An empty kind 448 is noise: it tells a requester nothing it did
            // not already know and still costs a group message.
            assertNull(f.push.buildTokenList(nostrGroupId))
        }

    @Test
    fun `an empty request is recognised and a self update is not`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            assertTrue(f.push.isTokenRequest(f.push.buildTokenRequest()))
            assertFalse(f.push.isTokenRequest(f.selfUpdate()))
        }

    @Test
    fun `an entry relayed by another member is applied on its own signature`() =
        runBlocking {
            // Two accounts, one group each, same MLS group id would be ideal —
            // but the point is narrower and testable here: applying an entry
            // does not consult who carried it, only whether the signature
            // verifies and the named member is current.
            val f = Fixture()
            f.createGroup()
            val announced = f.selfUpdate()

            val relayed = Fixture()
            // A fresh coordinator over the same manager stands in for a peer
            // that only ever saw the gossip, never the sender.
            val peer = MarmotPushCoordinator(f.manager, InMemoryPushStateStore())
            assertTrue(peer.apply(nostrGroupId, announced))
            assertEquals(1, peer.activeRecords(nostrGroupId).size)
            assertTrue(relayed.stateStore.load(nostrGroupId) == null)
        }

    @Test
    fun `a properly signed entry naming a non-member is still dropped`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val groupIdHex = assertNotNull(f.manager.mlsGroupIdHex(nostrGroupId))

            // A real proof from an account that simply holds no leaf here. The
            // signature verifies; membership is the separate gate, and it has
            // to be, or anyone who ever learns a group id could point its
            // members' notifications at a server of their choosing.
            val outsiderPriv = ByteArray(32).also { it[31] = 7 }
            val outsider = Nip01Crypto.pubKeyCreate(outsiderPriv).toHexKey()
            val fingerprint = PushSignedRecord.fingerprintOf(PushPlatform.APNS, deviceToken)
            val encryptedToken = TokenEncryption.encrypt(PushPlatform.APNS, deviceToken, server.hexToByteArray())
            val ownerTs = 1_735_680_000_000L
            val tags =
                PushOwnerProof.tags(
                    PushRecordKind.TOKEN,
                    groupIdHex,
                    outsider,
                    0,
                    PushPlatform.APNS.wireName,
                    server,
                    fingerprint,
                    ownerTs,
                    "",
                )
            val entry =
                PushTokenEntry(
                    memberIdHex = outsider,
                    leafIndex = 0,
                    platform = PushPlatform.APNS,
                    tokenFingerprint = fingerprint,
                    serverPubKeyHex = server,
                    relayHint = "",
                    encryptedToken = assertNotNull(PushBase64.decodeOrNull(encryptedToken)),
                    ownerTsMillis = ownerTs,
                    ownerSig = Nip01Crypto.sign(PushOwnerProof.eventId(outsider, tags, encryptedToken), outsiderPriv),
                )
            assertTrue(entry.verifyOwner(groupIdHex, currentProfileGroup = false), "the fixture should sign a real proof")

            val carried =
                Event("0".repeat(64), f.signer.pubKey, 1L, TokenRequestEvent.KIND, emptyArray(), PushGossip.encodeTokens(listOf(entry)), "")
            assertFalse(f.push.apply(nostrGroupId, carried))
            assertTrue(f.push.activeRecords(nostrGroupId).isEmpty())
        }

    @Test
    fun `a removal revokes the record and the tombstone survives a restart`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val announced = f.selfUpdate(ownerTs = 1_735_680_000_000L)

            val removal =
                assertNotNull(
                    f.push.buildRemoval(nostrGroupId, PushPlatform.APNS, deviceToken, server, ownerTsMillis = 1_735_680_001_000L),
                )
            assertEquals(TokenRemovalEvent.KIND, removal.kind)
            assertTrue(f.push.activeRecords(nostrGroupId).isEmpty())

            // A member that assembled a kind 448 before the removal delivers it
            // after. A restarted client must still refuse it — the tombstone is
            // the only durable thing that recognises it as stale, and a relayed
            // record's carrying epoch is unbounded.
            val restarted = MarmotPushCoordinator(f.manager, f.stateStore)
            assertFalse(restarted.apply(nostrGroupId, announced))
            assertTrue(restarted.activeRecords(nostrGroupId).isEmpty())
        }

    @Test
    fun `a newer registration clears the tombstone`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.selfUpdate(ownerTs = 1_735_680_000_000L)
            f.push.buildRemoval(nostrGroupId, PushPlatform.APNS, deviceToken, server, ownerTsMillis = 1_735_680_001_000L)

            f.selfUpdate(ownerTs = 1_735_680_002_000L)
            assertEquals(1, f.push.activeRecords(nostrGroupId).size)
        }

    @Test
    fun `a removed leaf loses its records entirely`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.selfUpdate()

            val leafIndex = assertNotNull(f.manager.leafIndexOf(nostrGroupId, f.signer.pubKey))
            f.push.forgetLeaf(nostrGroupId, f.signer.pubKey, leafIndex)
            assertTrue(f.push.activeRecords(nostrGroupId).isEmpty())
        }

    @Test
    fun `a trigger carries the encrypted tokens and nothing else`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.selfUpdate()

            val trigger = assertNotNull(f.push.buildTrigger(nostrGroupId, server, padding = 3))
            assertEquals(446, trigger.kind)
            // A fresh ephemeral key, so the server cannot link two triggers to
            // one sender — nor dedup on the outer id, which is why the spec
            // keys dedup on the content hash instead.
            assertFalse(trigger.pubKey == f.signer.pubKey)
            assertEquals(1, trigger.tags.size)
            assertEquals(listOf("v", PushGossip.VERSION), trigger.tags.single().toList())

            val chunks = assertNotNull(Event.fromJson(trigger.toJson()).let { _ -> f.chunksOf(trigger) })
            assertEquals(4, chunks.size)
            assertTrue(
                chunks.any {
                    it.toHexKey() ==
                        f.push
                            .activeRecords(nostrGroupId)
                            .single()
                            .encryptedToken
                            .toHexKey()
                },
            )
        }

    @Test
    fun `nothing to wake means no trigger`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            assertNull(f.push.buildTrigger(nostrGroupId, server))
        }

    @Test
    fun `an unreadable payload changes nothing and does not throw`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val junk =
                Event("0".repeat(64), f.signer.pubKey, 1L, TokenListEvent.KIND, emptyArray(), "{not json", "")
            assertFalse(f.push.apply(nostrGroupId, junk))
            assertTrue(f.push.activeRecords(nostrGroupId).isEmpty())
        }

    private fun Fixture.chunksOf(trigger: Event): List<ByteArray>? =
        com.vitorpamplona.quartz.marmot.mip05PushNotifications
            .NotificationRequestEvent(
                trigger.id,
                trigger.pubKey,
                trigger.createdAt,
                trigger.tags,
                trigger.content,
                trigger.sig,
            ).chunks()
}
