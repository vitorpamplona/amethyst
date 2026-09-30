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
package com.vitorpamplona.quartz.concord.cord07Voice

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordVoiceTest {
    private val alice = KeyPair().pubKey.toHexKey()
    private val bob = KeyPair().pubKey.toHexKey()
    private val carol = KeyPair().pubKey.toHexKey()
    private val channelId = "42".repeat(32)

    @Test
    fun presenceRoundTrips() {
        val rumor = VoicePresence.joined(alice, channelId, epoch = 0, identity = "sfu-abc", createdAt = 1_700_000_000L, broker = "https://broker.example")
        val info = VoicePresence.parse(rumor)
        assertEquals(VoicePresence.KIND, rumor.kind)
        assertEquals("sfu-abc", info?.identity)
        assertEquals("https://broker.example", info?.broker)
        assertEquals(channelId, info?.channelId)
        assertEquals(0L, info?.epoch)
        assertTrue(info?.joined == true)
        // The examples' tag order: channel, epoch, identity, broker, ms.
        assertEquals(listOf("channel", "epoch", "identity", "broker", "ms"), rumor.tags.map { it[0] })
    }

    @Test
    fun onlyUncontestedIdentitiesVerify() {
        val aliceP = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-x", 1L))!!
        val bobP = VoicePresence.parse(VoicePresence.joined(bob, channelId, 0, "id-bob", 1L))!!
        val carolP = VoicePresence.parse(VoicePresence.joined(carol, channelId, 0, "id-x", 1L))!!

        val fold = VoicePresence.fold(listOf(aliceP, bobP, carolP), nowMs = 1_000L)
        assertEquals(bob, fold.verified["id-bob"])
        assertFalse(fold.verified.containsKey("id-x")) // Alice and Carol both claim it: contested
        assertEquals(3, fold.present.size)
    }

    @Test
    fun latestPresencePerAuthorWins() {
        // Alice joined as id-a, left, then rejoined as id-b: only her latest counts.
        val joinedA = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-a", 10L, subMs = 0))!!
        val left = VoicePresence.parse(VoicePresence.left(alice, channelId, 0, 20L, subMs = 0))!!
        val joinedB = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-b", 30L, subMs = 0))!!
        // Bob's older heartbeat claimed id-b too, but his latest presence is a left.
        val bobOld = VoicePresence.parse(VoicePresence.joined(bob, channelId, 0, "id-b", 5L, subMs = 0))!!
        val bobLeft = VoicePresence.parse(VoicePresence.left(bob, channelId, 0, 6L, subMs = 0))!!

        // Arrival order must not matter.
        val fold = VoicePresence.fold(listOf(joinedB, bobLeft, joinedA, left, bobOld), nowMs = 31_000L)
        assertEquals(listOf(alice), fold.present.map { it.author })
        assertEquals("id-b", fold.present.single().identity)
        assertEquals(mapOf("id-b" to alice), fold.verified)

        // Before the rejoin arrived, Alice's latest is the left: absent.
        assertTrue(VoicePresence.fold(listOf(left, joinedA), nowMs = 21_000L).present.isEmpty())
    }

    @Test
    fun msTagOrdersPresencesWithinOneSecond() {
        val early = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-a", 10L, subMs = 100))!!
        val late = VoicePresence.parse(VoicePresence.left(alice, channelId, 0, 10L, subMs = 900))!!
        assertEquals(10_100L, early.ms)
        assertEquals(10_900L, late.ms)
        assertTrue(VoicePresence.fold(listOf(late, early), nowMs = 11_000L).present.isEmpty())
    }

    @Test
    fun equalTimeTiesBreakByLowerRumorId() {
        val a = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-a", 10L, subMs = 0))!!
        val b = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-b", 10L, subMs = 0))!!
        val winner = if (a.rumorId < b.rumorId) a else b
        assertEquals(winner.rumorId, VoicePresence.latestPerAuthor(listOf(a, b))[alice]?.rumorId)
        assertEquals(winner.rumorId, VoicePresence.latestPerAuthor(listOf(b, a))[alice]?.rumorId)
    }

    @Test
    fun staleLatestJoinedIsAbsent() {
        val p = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-a", 10L, subMs = 0))!!
        assertEquals(1, VoicePresence.fold(listOf(p), nowMs = 10_000L + VoicePresence.STALE_MS).present.size)
        assertTrue(VoicePresence.fold(listOf(p), nowMs = 10_001L + VoicePresence.STALE_MS).present.isEmpty())
    }

    @Test
    fun malformedPresenceIsDropped() {
        val base = VoicePresence.joined(alice, channelId, 0, "id-a", 10L, subMs = 0)
        assertNull(VoicePresence.parse(Event(base.id, base.pubKey, base.createdAt, base.kind, base.tags, "here", "")))
        val noIdentity = base.tags.filterNot { it[0] == VoicePresence.TAG_IDENTITY }.toTypedArray()
        assertNull(VoicePresence.parse(Event(base.id, base.pubKey, base.createdAt, base.kind, noIdentity, "joined", "")))
        val badMs = base.tags.map { if (it[0] == "ms") arrayOf("ms", "1000") else it }.toTypedArray()
        assertNull(VoicePresence.parse(Event(base.id, base.pubKey, base.createdAt, base.kind, badMs, "joined", "")))
    }

    @Test
    fun stalePresenceIsNotFresh() {
        val info = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id", createdAt = 1_000L))!!
        // createdAt is unix seconds; 1_000s -> 1_000_000ms
        assertTrue(VoicePresence.isFresh(info, nowMs = 1_000_000L + VoicePresence.STALE_MS))
        assertFalse(VoicePresence.isFresh(info, nowMs = 1_000_000L + VoicePresence.STALE_MS + 1))
    }

    @Test
    fun brokerTokenIsSignedByTheVoiceRoomKey() {
        val channelSecret = ByteArray(32) { 0x5A }
        val voiceSigner = ConcordKeyDerivation.voiceSignerKey(channelSecret, channelId.chunkedToBytes(), epoch = 0)
        val url = "https://broker.example" + ConcordBrokerToken.wellKnownPath(voiceSigner.publicKeyHex)

        val event = ConcordBrokerToken.buildAuthEvent(voiceSigner, url, createdAt = 1_700_000_000L)
        assertEquals(ConcordBrokerToken.KIND, event.kind)
        assertEquals(voiceSigner.publicKeyHex, event.pubKey) // the SFU room = voice key pubkey
        assertTrue(event.verify())

        val header = ConcordBrokerToken.authorizationHeader(event)
        assertTrue(header.startsWith("Concord "))
    }

    @Test
    fun sameSecondBrokerGrantsNeverShareAnId() {
        // Every member signs with the same voice_key.sk; without the nonce two joiners in one
        // second build one id and the broker's anti-replay set drops the second (CORD-07 §2).
        val voiceSigner = ConcordKeyDerivation.voiceSignerKey(ByteArray(32) { 0x5A }, channelId.chunkedToBytes(), epoch = 0)
        val url = "https://broker.example" + ConcordBrokerToken.wellKnownPath(voiceSigner.publicKeyHex)
        val a = ConcordBrokerToken.buildAuthEvent(voiceSigner, url, createdAt = 1_700_000_000L)
        val b = ConcordBrokerToken.buildAuthEvent(voiceSigner, url, createdAt = 1_700_000_000L)

        val nonceOf = { e: Event -> e.tags.firstOrNull { it[0] == ConcordBrokerToken.TAG_NONCE }?.getOrNull(1) }
        assertTrue(Regex("^[0-9a-f]{64}$").matches(nonceOf(a)!!))
        assertNotEquals(nonceOf(a), nonceOf(b))
        assertNotEquals(a.id, b.id)
        // Tag order matches the reference client: u, method, nonce.
        assertEquals(listOf("u", "method", "nonce"), a.tags.map { it[0] })
    }

    private fun String.chunkedToBytes(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
