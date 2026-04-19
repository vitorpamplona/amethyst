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
package com.vitorpamplona.amethyst.ui.components.pdf

import coil3.disk.DiskCache
import com.vitorpamplona.amethyst.Amethyst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException

object PdfFetcher {
    /**
     * Returns a snapshot of the cached PDF for [url], downloading it if necessary. The caller is
     * responsible for closing the returned snapshot; while it's open the cache entry cannot be
     * evicted, so the underlying file stays valid for `PdfRenderer`.
     *
     * Reuses the Coil disk cache (`Amethyst.instance.diskCache`) so PDFs share the same LRU
     * eviction and disk budget as images.
     */
    suspend fun fetchSnapshot(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
    ): DiskCache.Snapshot {
        val diskCache = Amethyst.instance.diskCache
        diskCache.openSnapshot(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            val editor = diskCache.openEditor(url) ?: throw IOException("Unable to open cache editor for $url")
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()

                okHttpClient(url).newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("PDF download failed: ${response.code}")
                    }
                    diskCache.fileSystem.write(editor.data) {
                        val bytes = writeAll(response.body.source())
                        if (bytes == 0L) throw IOException("PDF download failed: empty response body")
                    }
                }

                editor.commitAndOpenSnapshot() ?: throw IOException("Unable to commit cache editor for $url")
            } catch (t: Throwable) {
                runCatching { editor.abort() }
                throw t
            }
        }
    }
}
