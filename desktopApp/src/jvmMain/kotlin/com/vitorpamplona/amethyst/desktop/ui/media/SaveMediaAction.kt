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
package com.vitorpamplona.amethyst.desktop.ui.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object SaveMediaAction {
    private val httpClient = OkHttpClient()

    /**
     * Opens a save dialog and downloads the media URL to the chosen file.
     * Returns the saved file path or null if cancelled/failed.
     */
    suspend fun saveMedia(
        url: String,
        suggestedFilename: String? = null,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
    ): File? {
        val filename = suggestedFilename ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "media" }

        // FileDialog must be shown on EDT
        val file =
            withContext(Dispatchers.Main) {
                val dialog =
                    FileDialog(null as Frame?, "Save Media", FileDialog.SAVE).apply {
                        this.file = filename
                    }
                dialog.isVisible = true

                val dir = dialog.directory ?: return@withContext null
                File(dir, dialog.file ?: return@withContext null)
            } ?: return null

        // Download on IO
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val total = resp.body.contentLength()
                    resp.body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                onProgress?.invoke(downloaded, total)
                            }
                        }
                    }
                }
                file
            } catch (_: Exception) {
                null
            }
        }
    }
}
