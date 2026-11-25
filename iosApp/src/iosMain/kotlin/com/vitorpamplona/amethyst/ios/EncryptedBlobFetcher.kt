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
package com.vitorpamplona.amethyst.ios

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.service.http.DecryptInformation
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.decryptBlob
import okio.Buffer
import okio.FileSystem

/**
 * Pictures sent encrypted (NIP-17 kind 15, Marmot and Concord media): their URL is in the
 * account's [EncryptionKeyCache], so this fetches the blob, decrypts it and hands Coil the
 * picture, as Android's `EncryptedBlobInterceptor` does inside OkHttp. Any other URL goes on to
 * the network fetcher.
 */
class EncryptedBlobFetcher(
    private val url: String,
    private val info: DecryptInformation,
    private val download: suspend (String) -> ByteArray?,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = download(url) ?: throw IllegalStateException("Could not download $url")
        val plain = info.decryptBlob(bytes) ?: throw IllegalStateException("Could not decrypt $url")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(plain), FileSystem.SYSTEM),
            mimeType = info.mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val keys: EncryptionKeyCache,
        private val download: suspend (String) -> ByteArray?,
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val url = data.toString()
            val info = keys.get(url) ?: return null
            return EncryptedBlobFetcher(url, info, download)
        }
    }
}
