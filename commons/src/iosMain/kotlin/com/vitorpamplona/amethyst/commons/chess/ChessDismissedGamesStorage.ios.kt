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

// Phase 2 compile-only iOS actual. In-memory only; persistence via
// NSUserDefaults arrives with the iosApp module in Phase 3.
actual class ChessDismissedGamesStorage private actual constructor() {
    private val dismissed = mutableMapOf<String, Set<String>>()

    actual companion object {
        actual fun create(context: Any?): ChessDismissedGamesStorage = ChessDismissedGamesStorage()
    }

    actual fun load(userPubkey: String): Set<String> = dismissed[userPubkey] ?: emptySet()

    actual fun save(
        userPubkey: String,
        ids: Set<String>,
    ) {
        if (ids.isEmpty()) {
            dismissed.remove(userPubkey)
        } else {
            dismissed[userPubkey] = ids
        }
    }
}
