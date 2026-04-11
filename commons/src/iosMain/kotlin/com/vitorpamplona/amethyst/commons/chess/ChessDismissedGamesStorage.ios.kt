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

import platform.Foundation.NSUserDefaults

private const val KEY_PREFIX = "chess_dismissed_"

actual class ChessDismissedGamesStorage private actual constructor() {
    actual companion object {
        actual fun create(context: Any?): ChessDismissedGamesStorage = ChessDismissedGamesStorage()
    }

    @Suppress("UNCHECKED_CAST")
    actual fun load(userPubkey: String): Set<String> {
        val key = KEY_PREFIX + userPubkey
        val array = NSUserDefaults.standardUserDefaults.stringArrayForKey(key)
        return (array as? List<String>)?.toSet() ?: emptySet()
    }

    actual fun save(
        userPubkey: String,
        ids: Set<String>,
    ) {
        val key = KEY_PREFIX + userPubkey
        NSUserDefaults.standardUserDefaults.setObject(ids.toList(), forKey = key)
    }
}
