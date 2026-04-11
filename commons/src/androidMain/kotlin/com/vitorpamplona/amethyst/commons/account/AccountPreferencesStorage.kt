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
package com.vitorpamplona.amethyst.commons.account

import android.content.Context
import android.content.SharedPreferences

actual class AccountPreferencesStorage private constructor(
    private val prefs: SharedPreferences,
) {
    actual companion object {
        private const val PREFS_NAME = "amethyst_account_prefs"
        private const val KEY_LAST_NPUB = "last_npub"
        private const val KEY_BUNKER_URI = "bunker_uri"

        actual fun create(context: Any?): AccountPreferencesStorage {
            require(context is Context) { "Android context required" }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AccountPreferencesStorage(prefs)
        }
    }

    actual fun getLastNpub(): String? = prefs.getString(KEY_LAST_NPUB, null)?.takeIf { it.isNotEmpty() }

    actual fun saveLastNpub(npub: String) {
        prefs.edit().putString(KEY_LAST_NPUB, npub).apply()
    }

    actual fun clearLastNpub() {
        prefs.edit().remove(KEY_LAST_NPUB).apply()
    }

    actual fun getBunkerUri(): String? = prefs.getString(KEY_BUNKER_URI, null)?.takeIf { it.isNotEmpty() }

    actual fun saveBunkerUri(uri: String) {
        prefs.edit().putString(KEY_BUNKER_URI, uri).apply()
    }

    actual fun clearBunkerUri() {
        prefs.edit().remove(KEY_BUNKER_URI).apply()
    }

    actual fun hasBunkerUri(): Boolean = prefs.contains(KEY_BUNKER_URI)
}
