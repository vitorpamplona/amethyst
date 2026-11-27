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
package com.vitorpamplona.amethyst.commons.audio

import com.vitorpamplona.amethyst.commons.service.MediaSizeCache
import com.vitorpamplona.amethyst.commons.service.fileLength
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.parseContentRangeTotal
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray

/** Whether an MP4 carries audio only, read block by block with Range requests over the video role. */
internal actual suspend fun IRoleBasedHttpClientBuilder.readMp4AudioOnly(url: String): Boolean? {
    val client = ktorClientForVideo(url)
    return probeMp4AudioOnly { offset, length ->
        try {
            client.prepareGet(url) { header("Range", "bytes=$offset-${offset + length - 1}") }.execute { response ->
                when (response.status.value) {
                    // Past the end of the file: an empty read, which ends the walk.
                    416 -> ByteArray(0)
                    206 -> {
                        // The first answer says how big the whole file is: a held-back placeholder shows it.
                        if (offset == 0L) parseContentRangeTotal(response.headers["Content-Range"])?.let { MediaSizeCache.put(url, it) }
                        response.bodyAsChannel().readRemaining(length.toLong()).readByteArray()
                    }
                    // A server that ignores Range sends the whole file: that still answers the first block.
                    200 ->
                        if (offset == 0L) {
                            response.fileLength()?.let { MediaSizeCache.put(url, it) }
                            response.bodyAsChannel().readRemaining(length.toLong()).readByteArray()
                        } else {
                            null
                        }
                    else -> null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}
