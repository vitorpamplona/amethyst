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
package com.vitorpamplona.amethyst.commons.nip64Chess

import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.jester.JesterContent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who may sit where, and which moves count, as the board sees a Jester game. */
class ChessGameSnapshotTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    // fiatjaf's key; the expected value is jesterui's jesterPrivateStartGameRef, computed with
    // sha256(utf8(sha256hex(pubkey) + START_REF)) outside this codebase.
    @Test
    fun `private start ref matches jesterui`() {
        assertEquals(
            "7d06e03b044bdc9200caa570d117ed40ca06d35c7308264e6870266c9663b5c4",
            jesterPrivateStartRef("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"),
        )
    }

    @Test
    fun `open challenge refers to the start position and tags nobody`() {
        val template = buildJesterChallenge(Color.WHITE, null, createdAt = 1000)
        assertEquals(listOf(listOf("e", JesterProtocol.START_POSITION_HASH)), template.tags.map { it.toList() })

        val content = Json { ignoreUnknownKeys = true }.decodeFromString<JesterContent>(template.content)
        assertEquals(JesterProtocol.CONTENT_KIND_START, content.kind)
        assertTrue(content.history.isEmpty())
        assertTrue(content.nonce!!.matches(Regex("[0-9a-f]{8}")), "jesterui's nonce is 4 random bytes as hex")
    }

    @Test
    fun `direct challenge is filed under the opponent's private ref, not the public lobby`() {
        val template = buildJesterChallenge(Color.BLACK, bob, createdAt = 1000)
        assertEquals(
            listOf(listOf("e", jesterPrivateStartRef(bob)), listOf("p", bob)),
            template.tags.map { it.toList() },
        )
    }

    @Test
    fun `anyone can take the empty seat of an open challenge`() {
        val events = JesterGameEvents(start(alice, Color.WHITE), emptyList())

        val stranger = ChessGameSnapshot.build(events, bob)!!
        assertEquals(Color.BLACK, stranger.myColor)
        assertTrue(stranger.isOpenSeat)
        assertFalse(stranger.isMyTurn, "white moves first, and white is the challenger")

        val challenger = ChessGameSnapshot.build(events, alice)!!
        assertTrue(challenger.isWaitingForOpponent)
        assertTrue(challenger.isMyTurn)
    }

    @Test
    fun `a challenger playing black lets the joiner open as white`() {
        val events = JesterGameEvents(start(alice, Color.BLACK), emptyList())
        val joiner = ChessGameSnapshot.build(events, bob)!!
        assertEquals(Color.WHITE, joiner.myColor)
        assertTrue(joiner.isMyTurn)
    }

    @Test
    fun `a jesterui direct challenge seats only the player it was filed for`() {
        val start = start(alice, Color.WHITE, tags = arrayOf(arrayOf("e", jesterPrivateStartRef(bob))))
        val events = JesterGameEvents(start, emptyList())

        assertEquals(Color.BLACK, ChessGameSnapshot.build(events, bob)!!.myColor)
        assertNull(ChessGameSnapshot.build(events, carol)!!.myColor)
    }

    @Test
    fun `an outsider's move in a direct challenge does not take the seat`() {
        val start = start(alice, Color.WHITE, tags = arrayOf(arrayOf("e", jesterPrivateStartRef(bob))))
        val e4 = move("m1", alice, START, listOf("e4"), createdAt = 2000)
        val carolE5 = move("m2c", carol, "m1", listOf("e4", "e5"), createdAt = 2001)
        val bobC5 = move("m2b", bob, "m1", listOf("e4", "c5"), createdAt = 2002)

        val state = ChessGameSnapshot.build(JesterGameEvents(start, listOf(e4, carolE5, bobC5)), alice)!!
        assertEquals(bob, state.blackPubkey)
        assertEquals(listOf("e4", "c5"), state.state.moveHistory)
    }

    @Test
    fun `the earliest joiner of an open challenge wins the seat, whatever order relays deliver`() {
        val e4 = move("m1", alice, START, listOf("e4"), createdAt = 2000)
        val late = move("m2late", carol, "m1", listOf("e4", "e5"), createdAt = 2010)
        val early = move("m2early", bob, "m1", listOf("e4", "c5"), createdAt = 2005)

        val state = ChessGameSnapshot.build(JesterGameEvents(start(alice, Color.WHITE), listOf(e4, late, early)), carol)!!
        assertEquals(bob, state.blackPubkey)
        assertEquals(listOf("e4", "c5"), state.state.moveHistory)
        assertNull(state.myColor, "carol lost the race and is now a spectator")
    }

    @Test
    fun `a third party's longer history is not the game`() {
        val start = start(alice, Color.WHITE, tags = arrayOf(arrayOf("e", JesterProtocol.START_POSITION_HASH), arrayOf("p", bob)))
        val e4 = move("m1", alice, START, listOf("e4"), createdAt = 2000)
        val forged = move("m9", carol, "m1", listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#"), createdAt = 2001)

        val state = ChessGameSnapshot.build(JesterGameEvents(start, listOf(e4, forged)), bob)!!
        assertEquals(listOf("e4"), state.state.moveHistory)
        assertFalse(state.isFinished)
        assertTrue(state.isMyTurn)
    }

    @Test
    fun `a mate is a win for the mating side, a loss for the other, and neither for a spectator`() {
        val events = JesterGameEvents(start(alice, Color.WHITE), line(listOf("f3", "e5", "g4", "Qh4#")))

        val black = ChessGameSnapshot.build(events, bob)!!.outcome!!
        assertEquals(Color.BLACK, black.winner)
        assertEquals(ChessOutcome.Perspective.WON, black.perspective)
        assertEquals(GameTermination.CHECKMATE, black.termination)
        assertEquals(ChessOutcome.Perspective.LOST, ChessGameSnapshot.build(events, alice)!!.outcome!!.perspective)
        assertEquals(ChessOutcome.Perspective.SPECTATOR, ChessGameSnapshot.build(events, carol)!!.outcome!!.perspective)
    }

    @Test
    fun `a game in progress has no outcome`() {
        val events = JesterGameEvents(start(alice, Color.WHITE), line(listOf("e4", "e5")))
        assertNull(ChessGameSnapshot.build(events, alice)!!.outcome)
    }

    @Test
    fun `a resignation ends the game for the opponent, by resignation`() {
        val moves = line(listOf("e4", "e5"))
        val beforeResigning = ChessGameSnapshot.build(JesterGameEvents(start(alice, Color.WHITE), moves), bob)!!
        val template = beforeResigning.resignTemplate()!!
        val resign = JesterEvent("resign", bob, 3000, template.tags, template.content, "")

        val events = JesterGameEvents(start(alice, Color.WHITE), moves + resign)
        val white = ChessGameSnapshot.build(events, alice)!!.outcome!!
        assertEquals(Color.WHITE, white.winner)
        assertEquals(ChessOutcome.Perspective.WON, white.perspective)
        assertEquals(GameTermination.RESIGNATION, white.termination)
        assertEquals(ChessOutcome.Perspective.LOST, ChessGameSnapshot.build(events, bob)!!.outcome!!.perspective)
    }

    @Test
    fun `a stalemate is a draw for everyone`() {
        // Sam Loyd's ten-move stalemate: black, to move, has no legal move and is not in check.
        val loyd = listOf("e3", "a5", "Qh5", "Ra6", "Qxa5", "h5", "h4", "Rah6", "Qxc7", "f6", "Qxd7+", "Kf7", "Qxb7", "Qd3", "Qxb8", "Qh7", "Qxc8", "Kg6", "Qe6")
        val events = JesterGameEvents(start(alice, Color.WHITE), line(loyd))

        for (viewer in listOf(alice, bob, carol)) {
            val outcome = ChessGameSnapshot.build(events, viewer)!!.outcome!!
            assertNull(outcome.winner)
            assertEquals(ChessOutcome.Perspective.DRAW, outcome.perspective)
            assertEquals(GameTermination.STALEMATE, outcome.termination)
        }
    }

    @Test
    fun `an event ends its game when it carries a result or mates`() {
        val mate = line(listOf("f3", "e5", "g4", "Qh4#"))
        assertNull(mate[2].ending(), "a move that leaves the game going ends nothing")

        // jesterui's mate: no result field, only the # on the move.
        val jesteruiMate = mate[3].ending()!!
        assertEquals(Color.BLACK, jesteruiMate.winner)
        assertEquals(Color.BLACK, jesteruiMate.authorColor)
        assertEquals(GameTermination.CHECKMATE, jesteruiMate.termination)

        val moves = line(listOf("e4", "e5"))
        val template = ChessGameSnapshot.build(JesterGameEvents(start(alice, Color.WHITE), moves), alice)!!.resignTemplate()!!
        val resignation = JesterEvent("resign", alice, 3000, template.tags, template.content, "").ending()!!
        assertEquals(Color.BLACK, resignation.winner)
        assertEquals(Color.WHITE, resignation.authorColor, "white resigned, on its own move")
        assertEquals(GameTermination.RESIGNATION, resignation.termination)

        assertNull(start(alice, Color.WHITE).ending())
    }

    /** [sans] played alternately by alice (white) and bob (black), each move on the one before. */
    private fun line(sans: List<String>): List<JesterEvent> =
        sans.indices.map { i ->
            move("m$i", if (i % 2 == 0) alice else bob, if (i == 0) START else "m${i - 1}", sans.take(i + 1), createdAt = 2000L + i)
        }

    private fun start(
        author: String,
        color: Color,
        tags: Array<Array<String>> = arrayOf(arrayOf("e", JesterProtocol.START_POSITION_HASH)),
    ) = JesterEvent(
        id = START,
        pubKey = author,
        createdAt = 1000,
        tags = tags,
        content = """{"version":"0","kind":0,"history":[],"nonce":"00ff00ff","playerColor":"${if (color == Color.WHITE) "white" else "black"}"}""",
        sig = "",
    )

    private fun move(
        id: String,
        author: String,
        head: String,
        history: List<String>,
        createdAt: Long,
    ): JesterEvent {
        val engine = ChessEngine()
        history.forEach { engine.makeMove(it) }
        val historyJson = history.joinToString(",") { "\"$it\"" }
        return JesterEvent(
            id = id,
            pubKey = author,
            createdAt = createdAt,
            tags = arrayOf(arrayOf("e", START), arrayOf("e", head)),
            content = """{"version":"0","kind":1,"fen":"${engine.getFen()}","move":"${history.last()}","history":[$historyJson]}""",
            sig = "",
        )
    }

    companion object {
        const val START = "start-event"
    }
}
