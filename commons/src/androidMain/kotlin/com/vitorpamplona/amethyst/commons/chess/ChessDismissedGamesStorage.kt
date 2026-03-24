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

import android.content.Context
import android.content.SharedPreferences

actual class ChessDismissedGamesStorage private actual constructor() {
    private var prefs: SharedPreferences? = null

    actual companion object {
        private const val PREFS_NAME = "chess_dismissed_games"

        private fun prefsKey(userPubkey: String) = "dismissed_$userPubkey"

        actual fun create(context: Any?): ChessDismissedGamesStorage {
            val storage = ChessDismissedGamesStorage()
            val ctx =
                context as? Context
                    ?: throw IllegalArgumentException("Android context required")
            storage.prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return storage
        }
    }

    // getStringSet returns a live reference to the internal set — must copy defensively
    actual fun load(userPubkey: String): Set<String> = prefs?.getStringSet(prefsKey(userPubkey), null)?.toHashSet() ?: emptySet()

    actual fun save(
        userPubkey: String,
        ids: Set<String>,
    ) {
        prefs?.edit()?.putStringSet(prefsKey(userPubkey), ids)?.apply()
    }
}
