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
package com.vitorpamplona.amethyst.service.images

import androidx.compose.runtime.Stable
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.base64Image.Base64Image
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

@Stable
class Base64Fetcher(
    private val options: Options,
    private val data: Uri,
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        runCatching {
            ImageFetchResult(
                image = Base64Image.Companion.toBitmap(data.toString()).asImage(true),
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        }.getOrNull()

    object Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? =
            if (data.scheme == "data") {
                Base64Fetcher(options, data)
            } else {
                null
            }
    }

    object BKeyer : Keyer<Uri> {
        override fun key(
            data: Uri,
            options: Options,
        ): String? =
            if (data.scheme == "data") {
                sha256(data.toString().toByteArray()).toHexKey()
            } else {
                null
            }
    }
}
