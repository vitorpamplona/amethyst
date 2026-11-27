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
package com.vitorpamplona.amethyst.commons.podcasts

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray

/** Refuse bodies larger than this (chapters and transcripts are small text files), as the JVM does. */
private const val MAX_BYTES = 2_000_000L

/** A podcast episode's chapters or transcript, over the preview role, capped at [MAX_BYTES]. */
actual suspend fun IRoleBasedHttpClientBuilder.fetchPreviewText(url: String): String? =
    try {
        ktorClientForPreview(url).prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) return@execute null
            val declared = response.headers["Content-Length"]?.toLongOrNull()
            if (declared != null && declared > MAX_BYTES) return@execute null
            response
                .bodyAsChannel()
                .readRemaining(MAX_BYTES)
                .readByteArray()
                .decodeToString()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
