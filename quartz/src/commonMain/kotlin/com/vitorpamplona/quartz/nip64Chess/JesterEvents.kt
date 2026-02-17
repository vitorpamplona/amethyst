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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Jester Protocol Implementation
 *
 * Compatible with jesterui (https://github.com/jesterui/jesterui)
 *
 * Key differences from previous implementation:
 * - Single event kind (30) for all chess messages
 * - Content is JSON with: version, kind, fen, move, history, nonce
 * - Event linking via e-tags: [startId] or [startId, headId]
 * - Full move history included in every move event
 *
 * Content kind values:
 * - 0: Game start (challenge)
 * - 1: Move
 * - 2: Chat (not implemented)
 *
 * Reference: https://github.com/jesterui/jesterui/blob/devel/FLOW.md
 */
object JesterProtocol {
    /** Jester uses kind 30 for all chess events */
    const val KIND = 30

    /** SHA256 of starting FEN position - used as reference for game discovery */
    const val START_POSITION_HASH = "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"

    /** Standard starting FEN */
    const val FEN_START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    /** Content kind for game start */
    const val CONTENT_KIND_START = 0

    /** Content kind for move */
    const val CONTENT_KIND_MOVE = 1

    /** Content kind for chat */
    const val CONTENT_KIND_CHAT = 2
}

/**
 * JSON content structure for Jester events
 */
@Serializable
data class JesterContent(
    val version: String = "0",
    val kind: Int,
    val fen: String = JesterProtocol.FEN_START,
    val move: String? = null,
    val history: List<String> = emptyList(),
    val nonce: String? = null,
    // Extended fields for Amethyst (backward compatible - jesterui ignores unknown fields)
    val playerColor: String? = null, // "white" or "black" - challenger's color choice
    val result: String? = null, // "1-0", "0-1", "1/2-1/2" for game end
    val termination: String? = null, // "checkmate", "resignation", "draw_agreement", etc.
)

private val json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

/**
 * Jester Chess Event (Kind 30)
 *
 * Unified event class for all Jester chess messages.
 * The content.kind field determines the event type:
 * - 0: Game start/challenge
 * - 1: Move
 * - 2: Chat
 */
@Immutable
class JesterEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, JesterProtocol.KIND, tags, content, sig) {
    private val parsedContent: JesterContent? by lazy {
        try {
            json.decodeFromString<JesterContent>(content)
        } catch (e: Exception) {
            null
        }
    }

    /** Get the content kind (0=start, 1=move, 2=chat) */
    fun contentKind(): Int? = parsedContent?.kind

    /** Check if this is a game start event */
    fun isStartEvent(): Boolean = parsedContent?.kind == JesterProtocol.CONTENT_KIND_START

    /** Check if this is a move event */
    fun isMoveEvent(): Boolean = parsedContent?.kind == JesterProtocol.CONTENT_KIND_MOVE

    /** Get the FEN position */
    fun fen(): String? = parsedContent?.fen

    /** Get the latest move (SAN notation) */
    fun move(): String? = parsedContent?.move

    /** Get the full move history */
    fun history(): List<String> = parsedContent?.history ?: emptyList()

    /** Get the nonce (for start events) */
    fun nonce(): String? = parsedContent?.nonce

    /** Get the player color choice (for start events) */
    fun playerColor(): Color? =
        parsedContent?.playerColor?.let {
            if (it == "white") Color.WHITE else Color.BLACK
        }

    /** Get the game result (for end events) */
    fun result(): String? = parsedContent?.result

    /** Get the termination reason (for end events) */
    fun termination(): String? = parsedContent?.termination

    /** Get the start event ID (first e-tag) */
    fun startEventId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1)

    /** Get the head/parent move ID (second e-tag, for moves) */
    fun headEventId(): String? = tags.filter { it.size >= 2 && it[0] == "e" }.getOrNull(1)?.get(1)

    /** Get opponent pubkey (p-tag, for private games) */
    fun opponentPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    /** Get all e-tags */
    fun eTags(): List<String> = tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }

    companion object {
        const val KIND = JesterProtocol.KIND

        /**
         * Build a game start event (open challenge)
         *
         * @param playerColor Color the challenger wants to play
         * @param nonce Unique nonce for this game
         * @param createdAt Event timestamp
         */
        fun buildStart(
            playerColor: Color,
            nonce: String = generateNonce(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JesterEvent>.() -> Unit = {},
        ): EventTemplate<JesterEvent> {
            val content =
                JesterContent(
                    kind = JesterProtocol.CONTENT_KIND_START,
                    fen = JesterProtocol.FEN_START,
                    history = emptyList(),
                    nonce = nonce,
                    playerColor = if (playerColor == Color.WHITE) "white" else "black",
                )
            return eventTemplate(KIND, json.encodeToString(content), createdAt) {
                // Reference the standard start position for game discovery
                add(arrayOf("e", JesterProtocol.START_POSITION_HASH))
                initializer()
            }
        }

        /**
         * Build a private game start event (direct challenge)
         *
         * @param opponentPubkey Pubkey of the challenged player
         * @param playerColor Color the challenger wants to play
         * @param nonce Unique nonce for this game
         * @param createdAt Event timestamp
         */
        fun buildPrivateStart(
            opponentPubkey: String,
            playerColor: Color,
            nonce: String = generateNonce(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JesterEvent>.() -> Unit = {},
        ): EventTemplate<JesterEvent> {
            val content =
                JesterContent(
                    kind = JesterProtocol.CONTENT_KIND_START,
                    fen = JesterProtocol.FEN_START,
                    history = emptyList(),
                    nonce = nonce,
                    playerColor = if (playerColor == Color.WHITE) "white" else "black",
                )
            return eventTemplate(KIND, json.encodeToString(content), createdAt) {
                // Reference the standard start position
                add(arrayOf("e", JesterProtocol.START_POSITION_HASH))
                // Tag the opponent for notifications (only if valid pubkey)
                if (opponentPubkey.isNotEmpty() && opponentPubkey.length == 64) {
                    add(arrayOf("p", opponentPubkey))
                }
                initializer()
            }
        }

        /**
         * Build a move event
         *
         * @param startEventId ID of the game start event
         * @param headEventId ID of the previous move (or start event for first move)
         * @param move The move in SAN notation (e.g., "e4", "Nf3")
         * @param fen Resulting board position in FEN
         * @param history Complete move history including this move
         * @param opponentPubkey Opponent's pubkey for notifications
         * @param createdAt Event timestamp
         */
        fun buildMove(
            startEventId: String,
            headEventId: String,
            move: String,
            fen: String,
            history: List<String>,
            opponentPubkey: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JesterEvent>.() -> Unit = {},
        ): EventTemplate<JesterEvent> {
            val content =
                JesterContent(
                    kind = JesterProtocol.CONTENT_KIND_MOVE,
                    fen = fen,
                    move = move,
                    history = history,
                )
            return eventTemplate(KIND, json.encodeToString(content), createdAt) {
                // e-tags: [startId, headId]
                add(arrayOf("e", startEventId))
                add(arrayOf("e", headEventId))
                // Tag opponent for notifications (only if valid pubkey)
                if (opponentPubkey.isNotEmpty() && opponentPubkey.length == 64) {
                    add(arrayOf("p", opponentPubkey))
                }
                initializer()
            }
        }

        /**
         * Build a game end move (includes result in content)
         *
         * This is a move event with additional result/termination fields.
         * Used when the game ends (checkmate, resignation, draw).
         */
        fun buildEndMove(
            startEventId: String,
            headEventId: String,
            move: String?,
            fen: String,
            history: List<String>,
            opponentPubkey: String,
            result: GameResult,
            termination: GameTermination,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JesterEvent>.() -> Unit = {},
        ): EventTemplate<JesterEvent> {
            val content =
                JesterContent(
                    kind = JesterProtocol.CONTENT_KIND_MOVE,
                    fen = fen,
                    move = move,
                    history = history,
                    result = result.notation,
                    termination = termination.name.lowercase(),
                )
            return eventTemplate(KIND, json.encodeToString(content), createdAt) {
                add(arrayOf("e", startEventId))
                add(arrayOf("e", headEventId))
                // Tag opponent for notifications (only if valid pubkey)
                if (opponentPubkey.isNotEmpty() && opponentPubkey.length == 64) {
                    add(arrayOf("p", opponentPubkey))
                }
                initializer()
            }
        }

        private fun generateNonce(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..8).map { chars.random() }.joinToString("")
        }
    }
}

/**
 * Helper to check if an event is a Jester chess event
 */
fun Event.isJesterEvent(): Boolean = kind == JesterProtocol.KIND

/**
 * Helper to check if an event is a Jester start event
 */
fun Event.isJesterStartEvent(): Boolean {
    if (kind != JesterProtocol.KIND) return false
    return try {
        val content = json.decodeFromString<JesterContent>(this.content)
        content.kind == JesterProtocol.CONTENT_KIND_START && content.history.isEmpty()
    } catch (e: Exception) {
        false
    }
}

/**
 * Helper to check if an event is a Jester move event
 */
fun Event.isJesterMoveEvent(): Boolean {
    if (kind != JesterProtocol.KIND) return false
    return try {
        val content = json.decodeFromString<JesterContent>(this.content)
        content.kind == JesterProtocol.CONTENT_KIND_MOVE &&
            content.history.isNotEmpty() &&
            tags.count { it.size >= 2 && it[0] == "e" } == 2
    } catch (e: Exception) {
        false
    }
}

/**
 * Convert a raw Event to JesterEvent if valid
 */
fun Event.toJesterEvent(): JesterEvent? {
    if (kind != JesterProtocol.KIND) return null
    return JesterEvent(id, pubKey, createdAt, tags, content, sig)
}

/**
 * Game events container for Jester protocol
 */
data class JesterGameEvents(
    val startEvent: JesterEvent?,
    val moves: List<JesterEvent>,
) {
    companion object {
        fun empty() = JesterGameEvents(null, emptyList())
    }

    /** Get the latest move (with longest history) */
    fun latestMove(): JesterEvent? = moves.maxByOrNull { it.history().size }

    /** Get the current FEN position */
    fun currentFen(): String = latestMove()?.fen() ?: startEvent?.fen() ?: JesterProtocol.FEN_START

    /** Get the complete move history */
    fun fullHistory(): List<String> = latestMove()?.history() ?: emptyList()

    /** Check if game has ended */
    fun isEnded(): Boolean = latestMove()?.result() != null

    /** Get the game result if ended */
    fun result(): String? = latestMove()?.result()
}
