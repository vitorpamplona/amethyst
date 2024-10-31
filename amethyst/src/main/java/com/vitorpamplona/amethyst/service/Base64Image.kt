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
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Stable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.base64contentPattern
import java.util.Base64

@Stable
class Base64Fetcher(
    private val options: Options,
    private val data: Uri,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        checkNotInMainThread()

        val matcher = base64contentPattern.matcher(data.toString())

        if (matcher.find()) {
            val base64String = matcher.group(2)

            val byteArray = Base64.getDecoder().decode(base64String)
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) ?: throw Exception("Unable to load base64 $base64String")

            return ImageFetchResult(
                image = bitmap.asImage(true),
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        } else {
            throw Exception("Unable to load base64 $data")
        }
    }

    object Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            return if (base64contentPattern.matcher(data.toString()).find()) {
                return Base64Fetcher(options, data)
            } else {
                null
            }
        }
    }
}

object Base64Requester {
    fun imageRequest(
        context: Context,
        message: String,
    ): ImageRequest =
        ImageRequest
            .Builder(context)
            .data(message)
            .fetcherFactory(Base64Fetcher.Factory)
            .build()
}
