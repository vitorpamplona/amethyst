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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Legacy MIP-00 KeyPackages (kind 443).
 *
 * White Noise's MDK 0.8 still publishes its KeyPackages as kind 443, and the 2026-10-08 relay
 * census found users who publish nothing else. Quartz only typed kind 30443 and only asked relays
 * for 30443, so Amethyst could not find those users' KeyPackages and could not invite them.
 */
@OptIn(ExperimentalEncodingApi::class)
class LegacyKeyPackageEventTest {
    private val hub = InProcessRelays()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
        hub.close()
    }

    /**
     * A real kind 443 published by White Noise's MDK 0.8.0 (relay census 2026-10-08). Public key
     * material only: the content is the bare TLS KeyPackage (init key, leaf encryption and
     * signature keys, credential = the author's Nostr pubkey).
     */
    private val mdk080Json =
        """{"id":"1d83c042c3a77e851af8e0dac48f2d82dbb6047620c43ef79dd997dd15961084","pubkey":"4432f98af8f12d5cc896428a197519a5ac07fff3c670d716088a856d037007a7","created_at":1791418861,"kind":443,"tags":[["mls_protocol_version","1.0"],["mls_ciphersuite","0x0001"],["mls_extensions","0x000a","0xf2ee"],["mls_proposals","0x000a"],["relays","wss://relay.damus.io","wss://relay.primal.net"],["i","d6f1613adc3f9125d5ee573d8c706dd77c811d88802fe97c992d6b418f2a7c13"],["client","MDK/0.8.0"],["encoding","base64"]],"content":"AAEAASA7bBr5krMo3qNgX9yhCgKml2orYMwipD51ZCA3xghxAyA+xGOSqsn29GnkXJ4jkTpEqsMvLkkV6iyZP3UEKJjadiC8+rXHrIk3tb5QoHK+U/xijziwcrLd++oOh3qNaj+KvgABIEQy+Yr48S1cyJZCihl1GaWsB//zxnDXFgiKhW0DcAenAgABBAABGhoGAAry7srKBAAKqqoEAAHq6gEAAAAAasbT3QAAAABrNZ/tAEBA5yjIIEnGDcTJLr2r0lga1d51t3dmdiEIh2DdcAwNNM9CzbNZwPg0MzAD1CSL55lkkEGxcafYizGGtBAFaSvpDwMACgBAQKMzRb1q5AtQTwG1snfWLAIgF0dmppOD9FQys0D2nqC0fUAOs/Q1GeAu7W1csN2YqEaHBK0jJSgo+8lU9eSL+ws=","sig":"135abdb1991f23762e2e6365d51cd25d5b395d716abae9101430782e13518b97073d184b0d644e94aff0101f6a9b4c09196bf0247513acf6ecccb687f70e5231"}"""

    /** A real MDK 0.7.1 kind 443: same shape, but published before MIP-00 required `mls_proposals`. */
    private val mdk071Json =
        """{"id":"098ac3d44477eb6d66d41119fb19828aae7f6b036d3edcc38a4d9c474bdf646c","pubkey":"74eb3a135097e9d7d8f4c672cb99002a1281488429b444488f8c7b666afa84cd","created_at":1791424425,"kind":443,"tags":[["mls_protocol_version","1.0"],["mls_ciphersuite","0x0001"],["mls_extensions","0x000a","0xf2ee"],["relays","wss://relay.damus.io","wss://relay.primal.net","wss://nos.lol"],["i","f3b8f97110e7d7f4f970ae9ac038f19fe5416658de5279e66b44f838d4a69920"],["client","MDK/0.7.1"],["encoding","base64"]],"content":"AAEAASCcZ/0hIkxhfEHOabUFoO3xRB29/uBGBvvwUHh/RGwPQiDtlypxtjPk/v5hM372iFX1GCpmgfRiaqv1V2CjGN49aCCJThxiS7N7EODp3Q+ZT8l94b6MeVqVmv/++0ZCQpdclAABIHTrOhNQl+nX2PTGcsuZACoSgUiEKbRESI+Me2Zq+oTNAgABBAABenoGAAry7hoaAkpKBAABiooBAAAAAGrG6ZkAAAAAazW1qQBAQJeTOOAmitxtHANTimyU3rhvafMLgO4I7Sdr6270P7t/YXAxIQhJmajxG6tEsdV41fqopnWV7+5nFVwrIc8mVwgDAAoAQECX0OilThHc+y2eyDsu8gL5fadQ0CPLOiZdl6Bm1ncNE8wNMSP7YF6OMcOrTJbrFo4fxR6D18P3jZiRndmsAbgO","sig":"72c305890cc0b7e59813942236c0e07ebd1ca9fadb5100782d493476b353254a003f347f0aa92f72a2d41fdffcfc258a77bc4b5f147d5b9718f1281c6a4117ae"}"""

    private fun mdk080() = Event.fromJson(mdk080Json) as LegacyKeyPackageEvent

    /** The shape `rizq` publishes on kind 443: empty content and non-MIP tag names. */
    private fun rizqShaped(author: String = "0b75f11d1af8fe4de5f218f0a237ffa98d81b1ac30306e742f8d1e2fef20aa0e") =
        EventFactory.create<Event>(
            id = "0".repeat(64),
            pubKey = author,
            createdAt = 1791422303,
            kind = LegacyKeyPackageEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("mls_protocol_version", "1.0"),
                    arrayOf("ciphersuite", "0x0001"),
                    arrayOf("extensions", "nostr_group_data", "last_resort", "ratchet_tree"),
                    arrayOf("client", "rizq"),
                    arrayOf("relays", "wss://relay.damus.io", "wss://nos.lol"),
                ),
            content = "",
            sig = "0".repeat(128),
        )

    /** A fresh account with a real, MIP-era KeyPackage published as both kinds. */
    private class Peer(
        createdAt: Long,
    ) {
        val signer = NostrSignerInternal(KeyPair())
        val bundle =
            runBlocking {
                KeyPackageRotationManager().generateKeyPackage(signer.pubKey.hexToByteArray())
            }
        val base64 = Base64.encode(KeyPackageUtils.frameKeyPackage(bundle.keyPackage))
        val ref = bundle.keyPackage.reference().toHexKey()
        val relays = listOf(InProcessRelays.DEFAULT_URL)

        val addressable =
            runBlocking {
                signer.sign(KeyPackageEvent.build(base64, KeyPackageUtils.generateRandomDTag(), ref, relays, createdAt = createdAt))
            }

        fun legacy(at: Long) =
            runBlocking {
                signer.sign(LegacyKeyPackageEvent.build(base64, ref, relays, clientName = "MDK/0.8.0", createdAt = at))
            }
    }

    // ===== factory =====

    @Test
    fun kind443IsATypedKnownKind() {
        assertIs<LegacyKeyPackageEvent>(mdk080())
        assertTrue(EventFactory.isKnownKind(LegacyKeyPackageEvent.KIND))
        assertEquals(LegacyKeyPackageEvent.KIND, KeyPackageUtils.LEGACY_KIND)
        assertTrue(KeyPackageUtils.isKeyPackageKind(mdk080()))
    }

    // ===== a real White Noise package =====

    @Test
    fun mdkPackageReadsThroughTheSharedAccessors() {
        val event = mdk080()
        assertEquals("1.0", event.mlsProtocolVersion())
        assertEquals("0x0001", event.mlsCiphersuite())
        assertEquals(listOf("0x000a", "0xf2ee"), event.mlsExtensions())
        assertEquals(listOf("0x000a"), event.mlsProposals())
        assertEquals("base64", event.encoding())
        assertTrue(event.hasValidEncoding())
        assertEquals("d6f1613adc3f9125d5ee573d8c706dd77c811d88802fe97c992d6b418f2a7c13", event.keyPackageRef())
        assertEquals(listOf("wss://relay.damus.io/", "wss://relay.primal.net/"), event.relays()?.map { it.url })
        assertEquals("MDK/0.8.0", event.clientName())
        assertFalse(event.isCurrentProfile())
        assertNull(event.appComponents())
    }

    @Test
    fun mdkContentIsABareKeyPackageOwnedByTheAuthor() {
        val event = mdk080()
        val bytes = Base64.decode(event.keyPackageBase64())
        // `00 01 00 01`: version mls10 then ciphersuite 1, no MLSMessage envelope.
        assertContentEquals(byteArrayOf(0, 1, 0, 1), bytes.copyOfRange(0, 4))

        val keyPackage = KeyPackageUtils.decodeKeyPackage(bytes)
        assertEquals(event.keyPackageRef(), keyPackage.reference().toHexKey())
        val credential = assertIs<Credential.Basic>(keyPackage.leafNode.credential)
        assertEquals(event.pubKey, credential.identity.toHexKey())
        assertTrue(keyPackage.verifySignature())
        assertTrue(keyPackage.isLastResort(), "MDK marks every KeyPackage last_resort")
    }

    @Test
    fun mdk080PackagePassesTheSameValidationAs30443() {
        val event = mdk080()
        assertTrue(KeyPackageUtils.isValid(event))
        // Its lifetime starts an hour before created_at and spans exactly 84 days + 1h.
        assertTrue(KeyPackageUtils.isCryptographicallyValid(event, nowSeconds = event.createdAt))
        assertFalse(
            KeyPackageUtils.isCryptographicallyValid(event, nowSeconds = event.createdAt + KeyPackageUtils.MAX_LIFETIME_SECONDS),
            "an expired lifetime still fails",
        )
    }

    @Test
    fun mdk071PackageLacksMlsProposalsAndStaysInvalid() {
        // MIP-00 requires `mls_proposals` with self_remove (0x000a); MDK 0.7.1 predates that.
        // Validation is not relaxed for the legacy kind.
        val event = Event.fromJson(mdk071Json) as LegacyKeyPackageEvent
        assertNull(event.mlsProposals())
        assertFalse(KeyPackageUtils.isValid(event))
        assertFalse(KeyPackageUtils.isCryptographicallyValid(event, nowSeconds = event.createdAt))
    }

    @Test
    fun tamperedRefFailsDeepValidation() {
        val event = mdk080()
        val tampered =
            LegacyKeyPackageEvent(
                event.id,
                event.pubKey,
                event.createdAt,
                event.tags.map { if (it[0] == "i") arrayOf("i", "ab".repeat(32)) else it }.toTypedArray(),
                event.content,
                event.sig,
            )
        assertTrue(KeyPackageUtils.isValid(tampered))
        assertFalse(KeyPackageUtils.isCryptographicallyValid(tampered, nowSeconds = event.createdAt))
    }

    @Test
    fun legacyMayOmitTheRefButNot30443() {
        // MIP-00 "Validation differences": on kind 443 the `i` tag MAY be absent.
        val event = mdk080()
        val noRef =
            LegacyKeyPackageEvent(
                event.id,
                event.pubKey,
                event.createdAt,
                event.tags.filter { it[0] != "i" }.toTypedArray(),
                event.content,
                event.sig,
            )
        assertTrue(KeyPackageUtils.isCryptographicallyValid(noRef, nowSeconds = event.createdAt))
    }

    @Test
    fun legacyClaimingTheCurrentProfileIsInvalid() {
        val event = mdk080()
        val claimed =
            LegacyKeyPackageEvent(
                event.id,
                event.pubKey,
                event.createdAt,
                event.tags + arrayOf(arrayOf("app_components", "0x8009")),
                event.content,
                event.sig,
            )
        assertTrue(claimed.isCurrentProfile())
        assertFalse(KeyPackageUtils.isValid(claimed))
    }

    // ===== rizq =====

    @Test
    fun rizqShapedPackageParsesButIsNeverSelected() {
        val event = assertIs<LegacyKeyPackageEvent>(rizqShaped())
        assertEquals("1.0", event.mlsProtocolVersion())
        assertNull(event.mlsCiphersuite())
        assertNull(event.mlsExtensions())
        assertNull(event.mlsProposals())
        assertNull(event.encoding())
        assertNull(event.keyPackageRef())
        assertEquals("rizq", event.clientName())
        assertFalse(KeyPackageUtils.isValid(event))
        assertFalse(KeyPackageUtils.isCryptographicallyValid(event))
        assertNull(KeyPackageUtils.selectForInvite(listOf(event), event.pubKey))
    }

    @Test
    fun malformedTagsDoNotThrow() {
        val event =
            LegacyKeyPackageEvent(
                "0".repeat(64),
                "1".repeat(64),
                1,
                arrayOf(arrayOf("mls_ciphersuite"), arrayOf("mls_extensions"), arrayOf("i", ""), arrayOf("relays", "not a url"), arrayOf("encoding")),
                "!!not base64!!",
                "0".repeat(128),
            )
        assertNull(event.mlsCiphersuite())
        assertNull(event.mlsExtensions())
        assertNull(event.keyPackageRef())
        assertNull(event.relays())
        assertNull(event.encoding())
        assertFalse(KeyPackageUtils.isValid(event))
        assertFalse(KeyPackageUtils.isCryptographicallyValid(event))
    }

    // ===== builder =====

    @Test
    fun builderRoundTrips() {
        val relays = listOf(RelayUrlNormalizer.normalize("wss://relay.example.com/"))
        val template = LegacyKeyPackageEvent.build("AAE=", "ab".repeat(32), relays, clientName = "test", createdAt = 1234)
        assertEquals(LegacyKeyPackageEvent.KIND, template.kind)
        assertEquals("AAE=", template.content)
        assertEquals(1234, template.createdAt)
        assertTrue(template.tags.none { it[0] == "d" }, "kind 443 has no d slot")

        val event = LegacyKeyPackageEvent("0".repeat(64), "1".repeat(64), template.createdAt, template.tags, template.content, "0".repeat(128))
        assertEquals("1.0", event.mlsProtocolVersion())
        assertEquals("0x0001", event.mlsCiphersuite())
        assertEquals(listOf("0xf2ee", "0x000a"), event.mlsExtensions())
        assertEquals(listOf("0x000a"), event.mlsProposals())
        assertEquals("base64", event.encoding())
        assertEquals("ab".repeat(32), event.keyPackageRef())
        assertEquals(relays, event.relays())
        assertEquals("test", event.clientName())
        assertTrue(KeyPackageUtils.isValid(event))
    }

    // ===== selection =====

    @Test
    fun aValid30443IsPreferredOverANewer443() {
        val now = TimeUtils.now()
        val peer = Peer(createdAt = now - 600)
        val legacy = peer.legacy(now)
        val chosen = KeyPackageUtils.selectForInvite(listOf(legacy, peer.addressable), peer.signer.pubKey, now)
        assertEquals(peer.addressable.id, chosen?.id)
    }

    @Test
    fun theNewestValid443IsTheFallback() {
        val now = TimeUtils.now()
        val peer = Peer(createdAt = now)
        val older = peer.legacy(now - 600)
        val newer = peer.legacy(now - 60)
        val chosen = KeyPackageUtils.selectForInvite(listOf(older, newer), peer.signer.pubKey, now)
        assertIs<LegacyKeyPackageEvent>(chosen)
        assertEquals(newer.id, chosen.id)
    }

    @Test
    fun anInvalid30443FallsBackToAValid443() {
        val now = TimeUtils.now()
        val peer = Peer(createdAt = now)
        val broken =
            runBlocking {
                peer.signer.sign(KeyPackageEvent.build("AA==", peer.addressable.dTag(), peer.ref, peer.relays, createdAt = now))
            }
        val legacy = peer.legacy(now - 60)
        val chosen = KeyPackageUtils.selectForInvite(listOf(broken, legacy), peer.signer.pubKey, now)
        assertEquals(legacy.id, chosen?.id)
    }

    @Test
    fun packagesByAnotherAuthorAreIgnored() {
        val now = TimeUtils.now()
        val peer = Peer(createdAt = now)
        val stranger = Peer(createdAt = now)
        assertNull(KeyPackageUtils.selectForInvite(listOf(stranger.legacy(now), stranger.addressable), peer.signer.pubKey, now))
    }

    // ===== fetch =====

    @Test
    fun theInviteLookupFindsA443OnlyUser() =
        runBlocking {
            val client = NostrClient(hub, scope)
            val mdkUser = mdk080()
            assertTrue(client.publishAndConfirm(mdkUser, setOf(InProcessRelays.DEFAULT_URL)))

            // The bug: the 30443-only lookup finds nothing for this user.
            assertNull(
                KeyPackageFetcher.fetchKeyPackage(client, mdkUser.pubKey, setOf(InProcessRelays.DEFAULT_URL), idleTimeoutMs = 2_000, settleAfterFirstMs = 200),
            )

            val found =
                KeyPackageFetcher.fetchKeyPackageForInvite(
                    client = client,
                    targetPubKey = mdkUser.pubKey,
                    relays = setOf(InProcessRelays.DEFAULT_URL),
                    idleTimeoutMs = 2_000,
                    settleAfterFirstMs = 200,
                    nowSeconds = mdkUser.createdAt,
                )
            assertIs<LegacyKeyPackageEvent>(found)
            assertEquals(mdkUser.id, found.id)
            client.disconnect()
        }

    @Test
    fun theInviteLookupStillPrefers30443() =
        runBlocking {
            val client = NostrClient(hub, scope)
            val now = TimeUtils.now()
            val peer = Peer(createdAt = now - 600)
            val relays = setOf(InProcessRelays.DEFAULT_URL)
            assertTrue(client.publishAndConfirm(peer.addressable, relays))
            assertTrue(client.publishAndConfirm(peer.legacy(now), relays))

            val found =
                KeyPackageFetcher.fetchKeyPackageForInvite(client, peer.signer.pubKey, relays, idleTimeoutMs = 2_000, settleAfterFirstMs = 500, nowSeconds = now)
            assertEquals(peer.addressable.id, found?.id)
            client.disconnect()
        }
}
