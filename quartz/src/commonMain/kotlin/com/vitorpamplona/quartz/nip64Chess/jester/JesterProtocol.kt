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
package com.vitorpamplona.quartz.nip64Chess.jester

/**
 * Jester Protocol Constants
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
