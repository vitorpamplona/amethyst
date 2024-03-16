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
import android.net.Uri
import androidx.compose.runtime.Stable
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ImageRequest
import coil.request.Options
import com.vitorpamplona.amethyst.commons.robohash.Robohash
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import okio.buffer
import okio.source
import java.nio.charset.Charset

@Stable
class HashImageFetcher(
    private val context: Context,
    private val isLightTheme: Boolean,
    private val data: Uri,
) : Fetcher {
    override suspend fun fetch(): SourceResult {
        checkNotInMainThread()

        val source =
            try {
                Robohash.assemble(data.toString(), isLightTheme).byteInputStream(Charset.defaultCharset()).source().buffer()
            } finally {
            }

        return SourceResult(
            source = ImageSource(source, context),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY,
        )
    }

    object Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return HashImageFetcher(
                options.context,
                options.parameters.value("isLightTheme") ?: true,
                data,
            )
        }
    }
}

@Deprecated("Use the RobohashAssembler instead")
object RobohashImageRequest {
    fun build(
        context: Context,
        message: String,
        isLightTheme: Boolean,
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(message)
            .fetcherFactory(HashImageFetcher.Factory)
            .setParameter("isLightTheme", isLightTheme)
            .addHeader("Cache-Control", "max-age=31536000")
            .build()
    }
}
