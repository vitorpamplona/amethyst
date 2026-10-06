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
package com.vitorpamplona.quartz.nip64Chess.baseEvent

import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.draw.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseChessHintProviderTest {
    private val me = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val opponent = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val challenge = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private val opponentTag = arrayOf("p", opponent, relay)

    @Test
    fun everyLiveChessKindLinksTheOpponentWithTheirRelay() {
        val events: List<BaseChessEvent> =
            listOf(
                LiveChessGameChallengeEvent(id, me, 1, arrayOf(arrayOf("d", "g1"), opponentTag), "", sig),
                LiveChessMoveEvent(id, me, 1, arrayOf(arrayOf("d", "g1-1"), arrayOf("san", "e4"), opponentTag), "", sig),
                LiveChessDrawOfferEvent(id, me, 1, arrayOf(arrayOf("d", "g1"), opponentTag), "", sig),
            )

        events.forEach { event ->
            assertEquals(listOf(opponent), event.linkedPubKeys(), event::class.simpleName)
            assertEquals(listOf(opponent to relay), event.pubKeyHints().map { it.pubkey to it.relay.url })
        }
    }

    @Test
    fun anOpenChallengeLinksNobody() {
        val open = LiveChessGameChallengeEvent(id, me, 1, arrayOf(arrayOf("d", "g1")), "", sig)
        assertTrue(open.linkedPubKeys().isEmpty())
        assertTrue(open.pubKeyHints().isEmpty())
    }

    @Test
    fun theGameEndAlsoLinksTheWinner() {
        val end =
            LiveChessGameEndEvent(
                id,
                me,
                1,
                arrayOf(arrayOf("d", "g1"), arrayOf("result", "0-1"), arrayOf("winner", opponent), arrayOf("p", opponent)),
                "",
                sig,
            )

        assertEquals(listOf(opponent, opponent), end.linkedPubKeys())
        assertTrue(end.pubKeyHints().isEmpty())
    }

    @Test
    fun theAcceptLinksTheChallengeEvent() {
        val accept =
            LiveChessGameAcceptEvent(
                id,
                opponent,
                1,
                arrayOf(arrayOf("d", "g1"), arrayOf("e", challenge, relay, me), arrayOf("p", me)),
                "",
                sig,
            )

        assertEquals(listOf(challenge), accept.linkedEventIds())
        assertEquals(listOf(challenge to relay), accept.eventHints().map { it.eventId to it.relay.url })
        assertEquals(listOf(me), accept.linkedPubKeys())
    }

    @Test
    fun drawAndMoveMessagesAreSearchable() {
        val draw = LiveChessDrawOfferEvent(id, me, 1, arrayOf(arrayOf("d", "g1"), opponentTag), "Shall we call it a day?", sig)
        val move = LiveChessMoveEvent(id, me, 1, arrayOf(arrayOf("d", "g1-1"), opponentTag), "Brilliant sacrifice", sig)

        assertEquals("Shall we call it a day?", draw.indexableContent())
        assertEquals("Brilliant sacrifice", move.indexableContent())
    }
}
