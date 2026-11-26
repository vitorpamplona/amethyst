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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * The name of an account's own web storage jar (cookies, localStorage, IndexedDB), the same on
 * every host that keeps one jar per account: Android's WebView `Profile` and iOS's
 * `WKWebsiteDataStore(forIdentifier:)`. Every napplet and nSite of one account shares its jar,
 * so an app's storage survives a relaunch; two accounts never share one.
 *
 * A hash, not the pubkey: the browser's own files must not say which account they hold. It must
 * never change once shipped, or every account loses its apps' storage. Android's
 * `NappletWebViewProfiles` derives the same name (pinned by a test).
 */
object NappletStorageProfile {
    /** Domain separator, so this hash never collides with another use of SHA-256(pubkey). */
    private const val DOMAIN = "amethyst-webview-profile-v1:"

    /** 128 bits of the hash, in hex. */
    private const val NAME_LENGTH = 32

    /** The jar of [pubKey]; null for a blank scope (nobody logged in). */
    fun forPubKey(pubKey: HexKey): String? {
        if (pubKey.isBlank()) return null
        return sha256((DOMAIN + pubKey).encodeToByteArray()).toHexKey().take(NAME_LENGTH)
    }

    /** [forPubKey] as a UUID string (8-4-4-4-12), for a store that is named by UUID (iOS). */
    fun uuidFor(pubKey: HexKey): String? {
        val hex = forPubKey(pubKey) ?: return null
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32)
    }
}
