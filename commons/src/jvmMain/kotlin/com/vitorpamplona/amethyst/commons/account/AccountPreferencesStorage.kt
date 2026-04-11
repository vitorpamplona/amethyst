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

import java.io.File

actual class AccountPreferencesStorage private constructor(
    private val dir: File,
) {
    actual companion object {
        actual fun create(context: Any?): AccountPreferencesStorage {
            val homeDir = File(System.getProperty("user.home"))
            val amethystDir = File(homeDir, ".amethyst")
            if (!amethystDir.exists()) amethystDir.mkdirs()
            return AccountPreferencesStorage(amethystDir)
        }
    }

    private fun prefsFile() = File(dir, "last_account.txt")

    private fun bunkerFile() = File(dir, "bunker_uri.txt")

    actual fun getLastNpub(): String? =
        prefsFile()
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    actual fun saveLastNpub(npub: String) {
        prefsFile().writeText(npub)
    }

    actual fun clearLastNpub() {
        prefsFile().delete()
    }

    actual fun getBunkerUri(): String? =
        bunkerFile()
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    actual fun saveBunkerUri(uri: String) {
        bunkerFile().writeText(uri)
    }

    actual fun clearBunkerUri() {
        bunkerFile().delete()
    }

    actual fun hasBunkerUri(): Boolean = bunkerFile().exists()
}
