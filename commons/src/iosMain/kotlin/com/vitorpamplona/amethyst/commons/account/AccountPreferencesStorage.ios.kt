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

import platform.Foundation.NSUserDefaults

actual class AccountPreferencesStorage private constructor(
    private val defaults: NSUserDefaults,
) {
    actual companion object {
        private const val KEY_LAST_NPUB = "amethyst_last_npub"
        private const val KEY_BUNKER_URI = "amethyst_bunker_uri"

        actual fun create(context: Any?): AccountPreferencesStorage = AccountPreferencesStorage(NSUserDefaults.standardUserDefaults)
    }

    actual fun getLastNpub(): String? = defaults.stringForKey(KEY_LAST_NPUB)?.takeIf { it.isNotEmpty() }

    actual fun saveLastNpub(npub: String) {
        defaults.setObject(npub, KEY_LAST_NPUB)
    }

    actual fun clearLastNpub() {
        defaults.removeObjectForKey(KEY_LAST_NPUB)
    }

    actual fun getBunkerUri(): String? = defaults.stringForKey(KEY_BUNKER_URI)?.takeIf { it.isNotEmpty() }

    actual fun saveBunkerUri(uri: String) {
        defaults.setObject(uri, KEY_BUNKER_URI)
    }

    actual fun clearBunkerUri() {
        defaults.removeObjectForKey(KEY_BUNKER_URI)
    }

    actual fun hasBunkerUri(): Boolean = defaults.stringForKey(KEY_BUNKER_URI) != null
}
