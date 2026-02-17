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
 * Global registry of accepted game IDs.
 *
 * This singleton ensures that accepted game state is shared across all ViewModel instances.
 * Without this, different ViewModels (e.g., lobby vs game screen) would have separate
 * acceptedGameIds sets, causing the game screen to incorrectly load as spectator.
 */
object AcceptedGamesRegistry {
    private val acceptedGameIds = mutableSetOf<String>()
    private val lock = Any()

    fun markAsAccepted(gameId: String) {
        synchronized(lock) {
            acceptedGameIds.add(gameId)
        }
    }

    fun wasAccepted(gameId: String): Boolean =
        synchronized(lock) {
            acceptedGameIds.contains(gameId)
        }

    fun clear() {
        synchronized(lock) {
            acceptedGameIds.clear()
        }
    }

    /** Remove old entries - call periodically to prevent memory leak */
    fun clearOldEntries(keepGameIds: Set<String>) {
        synchronized(lock) {
            acceptedGameIds.retainAll(keepGameIds)
        }
    }
}
