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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Decrypt-once memo per plaintext hash → the on-disk file:// model. Object URLs never change for a
// given hash (content-addressed), so this is safe to keep for the process lifetime and bounded by the
// number of distinct community images a session touches.
private val resolvedByHash = ConcurrentHashMap<String, String>()

/**
 * Resolve a CORD-02 §6 community [pointer] to a model string for [RobohashFallbackAsyncImage]:
 *
 *  - **null / blank** → null (caller falls back to the robohash).
 *  - **plain URL** (a url-only pointer, e.g. Amethyst's own metadata form) → the URL, loaded directly.
 *  - **encrypted** (`key`/`nonce`/`hash` present) → fetch the ciphertext, AES-256-GCM-decrypt + verify
 *    the plaintext SHA-256 (all in [ImagePointer.decryptOrNull]), cache the plaintext to disk, and
 *    return its `file://` path. Returns null while loading or on any fetch/decrypt/integrity failure,
 *    so a swapped or unreachable blob simply shows the robohash instead of garbage.
 */
@Composable
fun rememberConcordImageModel(
    pointer: ImagePointer?,
    accountViewModel: AccountViewModel,
): String? {
    if (pointer == null) return null

    // A url-only pointer isn't encrypted media — hand the URL straight to Coil.
    if (!pointer.isResolvable()) return pointer.url.ifBlank { null }

    val context = LocalContext.current
    val model by produceState<String?>(resolvedByHash[pointer.hash], pointer, accountViewModel) {
        if (value != null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    resolvedByHash[pointer.hash]?.let { return@runCatching it }

                    val cacheFile = File(context.cacheDir, "concord-img-${pointer.hash}")
                    if (!cacheFile.exists()) {
                        val client = accountViewModel.httpClientBuilder.okHttpClientForImage(pointer.url)
                        val ciphertext =
                            client.newCall(Request.Builder().url(pointer.url).build()).execute().use { resp ->
                                if (!resp.isSuccessful) return@runCatching null
                                resp.body?.bytes()
                            } ?: return@runCatching null

                        val plaintext = pointer.decryptOrNull(ciphertext) ?: return@runCatching null
                        cacheFile.writeBytes(plaintext)
                    }
                    val uri = "file://${cacheFile.absolutePath}"
                    resolvedByHash[pointer.hash] = uri
                    uri
                }.onFailure { Log.w("ConcordImage", "Failed to resolve community image ${pointer.url}", it) }
                    .getOrNull()
            }
    }
    return model
}
