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
package com.vitorpamplona.amethyst.commons.nip64Chess.dal

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol

/** The game start note [this] Jester note belongs to: itself for a start, the note it replies to for a move. */
fun Note.chessGameStartNote(): Note? {
    val jester = event as? JesterEvent ?: return null
    return when {
        jester.isStartEvent() -> this
        jester.isMoveEvent() -> jester.startEventId()?.let { LocalCache.getNoteIfExists(it) }
        else -> null
    }
}

/** True when [this] is a loaded Jester start event. */
fun Note.isChessGameStart(): Boolean = (event as? JesterEvent)?.isStartEvent() == true

/** The last time anyone touched this game: its newest move, or the challenge itself. */
fun Note.chessLastActivity(): Long = replies.maxOfOrNull { it.createdAt() ?: 0L } ?: createdAt() ?: 0L

/** Newest activity first, so the games being played float to the top. */
fun Collection<Note>.sortedByChessActivity(): List<Note> = sortedWith(compareByDescending<Note> { it.chessLastActivity() }.thenBy { it.idHex })

/** Whether [pubkey] started, was challenged to (by `p` or by jesterui's private reference), or moved in this game. */
fun Note.isChessGameOf(pubkey: String): Boolean {
    val start = event as? JesterEvent ?: return false
    return start.pubKey == pubkey ||
        pubkey in start.opponentKeys() ||
        JesterProtocol.privateStartRef(pubkey) in start.eTags() ||
        replies.any { it.author?.pubkeyHex == pubkey }
}
