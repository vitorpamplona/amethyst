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
package com.vitorpamplona.amethyst.service

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import com.vitorpamplona.amethyst.commons.preview.BlurHashDecoder
import java.net.URLDecoder
import java.net.URLEncoder

@Stable
class BlurHashFetcher(
    private val options: Options,
    private val data: Uri,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        checkNotInMainThread()

        val hash = URLDecoder.decode(data.toString().removePrefix("bluehash:"), "utf-8")

        val bitmap = BlurHashDecoder.decodeKeepAspectRatio(hash, 25) ?: throw Exception("Unable to convert Bluehash $data")

        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    object Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = BlurHashFetcher(options, data)
    }
}

object BlurHashRequester {
    fun imageRequest(
        context: Context,
        message: String,
    ): ImageRequest {
        val encodedMessage = URLEncoder.encode(message, "utf-8")

        return ImageRequest
            .Builder(context)
            .data("bluehash:$encodedMessage")
            .fetcherFactory(BlurHashFetcher.Factory)
            .build()
    }
}
