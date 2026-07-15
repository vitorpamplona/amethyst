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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordVoiceTest {
    private val alice = KeyPair().pubKey.toHexKey()
    private val bob = KeyPair().pubKey.toHexKey()
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
    }

    @Test
    fun onlyUncontestedIdentitiesVerify() {
        val aliceP = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-alice", 1L))!!
        val bobP = VoicePresence.parse(VoicePresence.joined(bob, channelId, 0, "id-bob", 1L))!!
        // both Alice and Bob claim the same identity -> contested
        val contestedA = VoicePresence.parse(VoicePresence.joined(alice, channelId, 0, "id-x", 1L))!!
        val contestedB = VoicePresence.parse(VoicePresence.joined(bob, channelId, 0, "id-x", 1L))!!

        val verified = VoicePresence.verifiedParticipants(listOf(aliceP, bobP, contestedA, contestedB))
        assertEquals(alice, verified["id-alice"])
        assertEquals(bob, verified["id-bob"])
        assertFalse(verified.containsKey("id-x")) // contested identity omitted
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

    private fun String.chunkedToBytes(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
