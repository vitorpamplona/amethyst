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
package com.vitorpamplona.amethyst.commons.chess

/**
 * Global chess configuration shared across Android and Desktop.
 *
 * These relays are the primary relays used by Jester and other Nostr chess apps.
 * Using a small, fixed set ensures fast queries and reliable game discovery.
 */
object ChessConfig {
    /**
     * The 3 main relays for chess events.
     * These are used for both fetching and publishing chess events.
     */
    val CHESS_RELAYS =
        listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
        )

    /**
     * Display names for the chess relays (without protocol prefix)
     */
    val CHESS_RELAY_NAMES =
        listOf(
            "relay.damus.io",
            "nos.lol",
            "relay.primal.net",
        )

    /**
     * Timeout for relay queries in milliseconds.
     * With only 3 relays, we can wait for all of them.
     */
    const val FETCH_TIMEOUT_MS = 10_000L
}
