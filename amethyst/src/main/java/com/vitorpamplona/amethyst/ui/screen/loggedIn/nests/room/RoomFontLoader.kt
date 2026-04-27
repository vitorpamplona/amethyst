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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Download a font referenced by a NIP-53 `["f", family, url]` tag,
 * cache it on disk under the app's cache dir, and wrap the result
 * as a Compose [FontFamily] for typography overrides.
 *
 * The cache key is the SHA-256 of the URL — same URL → same file
 * across launches, so a returning user pays the network cost
 * exactly once. Failures (404, malformed font, Tor circuit
 * timeout) return null; the caller falls back to the family-name
 * mapping or platform default.
 *
 * Runs the actual download on [Dispatchers.IO] so the @Composable
 * call site can [androidx.compose.runtime.produceState] this
 * without blocking recomposition.
 */
internal object RoomFontLoader {
    suspend fun load(
        url: String,
        context: Context,
        clientFor: (String) -> OkHttpClient,
    ): FontFamily? =
        withContext(Dispatchers.IO) {
            runCatching {
                val cached = ensureCached(url, context, clientFor) ?: return@runCatching null
                // Compose's `Font(file)` builder reads the file lazily
                // when the typography is rendered, so the IO-thread
                // download here just needs to land the file on disk.
                FontFamily(Font(file = cached, weight = FontWeight.Normal, style = FontStyle.Normal))
            }.getOrNull()
        }

    private fun ensureCached(
        url: String,
        context: Context,
        clientFor: (String) -> OkHttpClient,
    ): File? {
        val cacheDir = File(context.cacheDir, "audio-room-fonts").also { it.mkdirs() }
        val file = File(cacheDir, hashName(url))
        if (file.exists() && file.length() > 0L) return file

        val client = clientFor(url)
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            file.outputStream().use { out -> resp.body.byteStream().copyTo(out) }
        }
        return file.takeIf { it.length() > 0L }
    }

    private fun hashName(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        // Hex SHA-256 — collision-resistant + filename-safe.
        return buildString(bytes.size * 2) {
            for (b in bytes) append(String.format("%02x", b))
        }
    }
}
