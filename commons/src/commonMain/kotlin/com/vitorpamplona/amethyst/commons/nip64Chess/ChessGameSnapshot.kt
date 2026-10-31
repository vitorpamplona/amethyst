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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.jester.toJesterEvent

/**
 * A Jester game as LocalCache holds it: the start note, with every move linked to it as a reply
 * (see `computeReplyTo`). Null when [this] is not a loaded Jester start event.
 */
fun Note.jesterGameEvents(): JesterGameEvents? {
    val start = event as? JesterEvent ?: return null
    if (!start.isStartEvent()) return null
    return JesterGameEvents(
        startEvent = start,
        moves = replies.mapNotNull { reply -> (reply.event as? JesterEvent)?.takeIf { it.isMoveEvent() } },
    )
}

/** The REQs that load one game: its start event, and every kind-30 event that points at it. */
fun chessGameFilters(gameId: HexKey): List<Filter> =
    listOf(
        Filter(ids = listOf(gameId)),
        Filter(kinds = listOf(JesterEvent.KIND), tags = mapOf("e" to listOf(gameId))),
    )

/**
 * The game [gameId] out of events as a relay hands them over, for code that has no LocalCache
 * (`amy`). Null until its start event is among them.
 */
fun jesterGameEvents(
    gameId: HexKey,
    events: Iterable<Event>,
): JesterGameEvents? {
    val jester = events.mapNotNull { it as? JesterEvent ?: it.toJesterEvent() }.distinctBy { it.id }
    val start = jester.firstOrNull { it.id == gameId && it.isStartEvent() } ?: return null
    return JesterGameEvents(start, jester.filter { it.isMoveEvent() && it.startEventId() == gameId })
}

/** The start note of the game [this] Jester event belongs to: itself for a start, its first `e` for a move. */
fun JesterEvent.gameId(): HexKey? = if (isStartEvent()) id else startEventId()

/**
 * One game as a viewer sees it right now, rebuilt from its events every time one arrives.
 *
 * [engine] is at the current position and belongs to this snapshot alone: nothing moves it, a
 * move is made on a copy and published, and the next snapshot comes from the published event.
 */
@Immutable
class ChessGameSnapshot(
    val gameId: HexKey,
    val state: ReconstructedGameState,
    val engine: ChessEngine,
    val challenger: HexKey,
    val viewerPubkey: HexKey,
    /** The colour the viewer plays, or can take: the empty seat of someone else's open challenge. Null for spectators. */
    val myColor: Color?,
    /** Every position from the start to now, for stepping back and for highlighting the last move. */
    val replay: ChessReplay,
) {
    val whitePubkey: HexKey? get() = state.whitePubkey
    val blackPubkey: HexKey? get() = state.blackPubkey

    val isFinished: Boolean get() = state.isFinished()

    /** An open challenge nobody has taken yet. */
    val isOpenSeat: Boolean get() = whitePubkey == null || blackPubkey == null

    /** The viewer's own challenge, still waiting for an opponent. */
    val isWaitingForOpponent: Boolean get() = isOpenSeat && challenger == viewerPubkey

    val isSpectator: Boolean get() = myColor == null

    val isMyTurn: Boolean get() = myColor != null && !isFinished && state.currentPosition.activeColor == myColor

    /** The other player, from the viewer's side; for a spectator, black. Null while the seat is open. */
    val opponentPubkey: HexKey? get() = if (myColor == Color.BLACK) whitePubkey else blackPubkey

    val moveCount: Int get() = state.moveHistory.size

    val lastActivityAt: Long get() = state.lastMoveAt ?: state.challengeCreatedAt

    /** True when [myColor] is a seat the viewer would take by moving, not one they hold. */
    val isJoinable: Boolean get() = isOpenSeat && myColor != null && !isWaitingForOpponent

    companion object {
        fun build(
            events: JesterGameEvents,
            viewerPubkey: HexKey,
        ): ChessGameSnapshot? {
            val result = ChessStateReconstructor.reconstruct(events, viewerPubkey) as? ReconstructionResult.Success ?: return null
            val state = result.state
            val start = events.startEvent ?: return null

            val myColor =
                when (state.viewerRole) {
                    ViewerRole.WHITE_PLAYER -> Color.WHITE
                    ViewerRole.BLACK_PLAYER -> Color.BLACK
                    ViewerRole.SPECTATOR ->
                        when {
                            !canTakeSeat(start, viewerPubkey) -> null
                            state.whitePubkey == null -> Color.WHITE
                            state.blackPubkey == null -> Color.BLACK
                            else -> null
                        }
                }

            return ChessGameSnapshot(start.id, state, result.engine, start.pubKey, viewerPubkey, myColor, ChessReplay.of(state.moveHistory))
        }

        /** An open challenge seats anyone; a direct one only the player it was filed for. */
        private fun canTakeSeat(
            start: JesterEvent,
            viewerPubkey: HexKey,
        ): Boolean {
            val privateRefs = start.eTags().filter { it != JesterProtocol.START_POSITION_HASH }
            return if (privateRefs.isEmpty()) start.opponentKeys().isEmpty() else jesterPrivateStartRef(viewerPubkey) in privateRefs
        }
    }
}
