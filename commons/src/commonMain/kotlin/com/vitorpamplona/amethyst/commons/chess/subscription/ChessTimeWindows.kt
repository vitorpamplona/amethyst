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
package com.vitorpamplona.amethyst.commons.chess.subscription

/**
 * Shared time window constants for chess subscriptions.
 * Used by both Android and Desktop to ensure consistent behavior.
 */
object ChessTimeWindows {
    /**
     * 24 hours - challenges older than this are considered expired.
     * Used for challenge and accept event filters.
     */
    const val CHALLENGE_WINDOW_SECONDS = 24 * 60 * 60L

    /**
     * 7 days - default lookback for game events when no EOSE cache exists.
     * This prevents loading ancient game history on first connection
     * while still allowing recovery of recent games.
     */
    const val GAME_EVENT_WINDOW_SECONDS = 7 * 24 * 60 * 60L
}
