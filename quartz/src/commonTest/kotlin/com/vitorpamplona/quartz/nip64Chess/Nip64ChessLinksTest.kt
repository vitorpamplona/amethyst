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
package com.vitorpamplona.quartz.nip64Chess

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.ChessResultProps
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip64ChessLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val opponent = "a".repeat(64)
    private val challenge = "2".repeat(64)
    private val start = "3".repeat(64)
    private val head = "4".repeat(64)

    @Test
    fun everyLiveChessEventNamesTheOpponent() {
        val tags = arrayOf(arrayOf("d", "game"), arrayOf("p", opponent))
        val expected = listOf(Link(Relation.OPPONENT, LinkTarget.User(opponent), "p"))
        assertEquals(expected, LiveChessGameChallengeEvent(id, author, 1, tags, "", sig).links())
        assertEquals(expected, LiveChessMoveEvent(id, author, 1, tags, "", sig).links())
    }

    @Test
    fun acceptNamesTheChallenge() {
        val event = LiveChessGameAcceptEvent(id, author, 1, arrayOf(arrayOf("d", "game"), arrayOf("e", challenge, "", opponent), arrayOf("p", opponent)), "", sig)
        assertEquals(
            listOf(
                Link(Relation.ACCEPTED, LinkTarget.Event(challenge), "e"),
                Link(Relation.OPPONENT, LinkTarget.User(opponent), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun endNamesTheWinnerAndCarriesTheResult() {
        val event =
            LiveChessGameEndEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("d", "game"),
                    arrayOf("result", "1-0"),
                    arrayOf("termination", "checkmate"),
                    arrayOf("winner", author),
                    arrayOf("p", opponent),
                ),
                "",
                sig,
            )
        val result = ChessResultProps("1-0", "checkmate")
        assertEquals(
            listOf(
                Link(Relation.OPPONENT, LinkTarget.User(opponent), "p", result),
                Link(Relation.WINNER, LinkTarget.User(author), "winner", result),
            ),
            event.links(),
        )
    }

    @Test
    fun jesterStartHashIsNotAnEvent() {
        val startEvent = JesterEvent(id, author, 1, arrayOf(arrayOf("e", JesterProtocol.START_POSITION_HASH), arrayOf("p", opponent)), "{}", sig)
        assertEquals(listOf(Link(Relation.OPPONENT, LinkTarget.User(opponent), "p")), startEvent.links())

        val move = JesterEvent(id, author, 1, arrayOf(arrayOf("e", start), arrayOf("e", head), arrayOf("p", opponent)), "{}", sig)
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(start), "e"),
                Link(Relation.PARENT, LinkTarget.Event(head), "e"),
                Link(Relation.OPPONENT, LinkTarget.User(opponent), "p"),
            ),
            move.links(),
        )
    }
}
