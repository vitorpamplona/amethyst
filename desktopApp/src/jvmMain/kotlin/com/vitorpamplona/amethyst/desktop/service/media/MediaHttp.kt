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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import okhttp3.OkHttpClient

/**
 * How the desktop media code (the player, video thumbnails, "save") fetches a URL. The shared-UI
 * window [install]s the app's role-based clients, which follow the user's Tor choice for media
 * and decrypt the encrypted blobs registered in [keyCache] (NIP-17 DM files, Cordn and Marmot
 * media). Until then — and in the legacy app — it is [DesktopHttpClient]'s client and no keys.
 */
object MediaHttp {
    @Volatile private var clientFor: (String) -> OkHttpClient = { DesktopHttpClient.currentClient() }

    /** The keys of the encrypted media the app has seen, by URL. */
    @Volatile var keyCache: EncryptionKeyCache? = null
        private set

    fun install(
        clientFor: (String) -> OkHttpClient,
        keyCache: EncryptionKeyCache,
    ) {
        this.clientFor = clientFor
        this.keyCache = keyCache
    }

    fun client(url: String): OkHttpClient = clientFor(url)

    /** Whether [url] is an encrypted blob the engine cannot stream: it has to be decrypted first. */
    fun isEncrypted(url: String): Boolean = keyCache?.get(url) != null

    /** The MIME type the event declared for the encrypted blob at [url], if any. */
    fun encryptedMimeType(url: String): String? = keyCache?.get(url)?.mimeType
}
