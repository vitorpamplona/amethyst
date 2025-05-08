/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

object ShareHelper {
    suspend fun shareImageFromUrl(
        context: Context,
        imageUrl: String,
    ) {
        val file = getCachedFileForUrl(context, imageUrl)

        if (file != null) {
            shareMediaFile(context, getSharableUri(context, file), "image/*")
        } else {
            throw IOException("Image file does not exist at path: $imageUrl")
        }
    }

    private suspend fun getCachedFileForUrl(
        context: Context,
        url: String,
    ): File? {
        val loader = context.imageLoader
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .size(Size.ORIGINAL)
                .allowHardware(true)
                .build()

        val result = loader.execute(request)
        if (result is SuccessResult) {
            val diskCacheKey = result.diskCacheKey ?: return null
            val snapshot = loader.diskCache?.openSnapshot(diskCacheKey)
            return snapshot?.data?.toFile()
        } else {
            Log.d("ShareHelper", "Failed to get cached file for URL: $url")
            return null
        }
    }

    private fun getSharableUri(
        context: Context,
        file: File,
    ): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )

    private fun shareMediaFile(
        context: Context,
        uri: Uri,
        mimeType: String = "image/*",
    ) {
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        CoroutineScope(Dispatchers.Main).launch {
            context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
        }
    }
}
