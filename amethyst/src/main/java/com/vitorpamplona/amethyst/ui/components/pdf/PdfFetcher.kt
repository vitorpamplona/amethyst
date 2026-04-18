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

import android.content.Context
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.sink
import java.io.File
import java.io.IOException

object PdfFetcher {
    private const val CACHE_DIR_NAME = "pdf_cache"

    private fun cacheDir(context: Context): File = File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }

    fun cachedFileOrNull(
        context: Context,
        url: String,
    ): File? {
        val file = cacheFile(context, url)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun cacheFile(
        context: Context,
        url: String,
    ): File {
        val key = sha256(url.toByteArray()).toHexKey()
        return File(cacheDir(context), "$key.pdf")
    }

    suspend fun fetch(
        context: Context,
        url: String,
        okHttpClient: (String) -> OkHttpClient,
    ): File =
        withContext(Dispatchers.IO) {
            val file = cacheFile(context, url)
            if (file.exists() && file.length() > 0) return@withContext file

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
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.outputStream().use { out ->
                    val bytes = response.body.source().readAll(out.sink())
                    if (bytes == 0L) throw IOException("PDF download failed: empty response body")
                }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }

            file
        }
}
