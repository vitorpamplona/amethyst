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
package com.vitorpamplona.amethyst.commons.service.image

import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.HttpException
import com.vitorpamplona.amethyst.commons.service.http.BlossomAuthorHints
import com.vitorpamplona.amethyst.commons.service.http.BlossomBlobName
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import com.vitorpamplona.quartz.utils.Log
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads a Blossom blob from the server its URL names, and when that server has lost it, or is
 * gone, from another server that holds the same sha256.
 *
 * A note's `https://host/<sha256>.jpg` names one server, but Blossom blobs are content-addressed
 * and usually mirrored: BUD-03 has the author list their servers in a kind 10063 event. Only the
 * failures that mean "not here" move on (404, 410, 5xx, and a host that can't be reached); a 401,
 * 402 or 403 is the server's answer about this blob, and the read-auth retry handles 401.
 *
 * The other server comes from [resolve], the BUD-10 resolver, asked for a `blossom:` URI that
 * carries the authors [BlossomAuthorHints] knows for the hash, so it probes their servers (and the
 * viewer's own) with a HEAD each. A resolver that finds nothing, or only the same URL, leaves the
 * original failure in place.
 *
 * [primary] and [fallback] build the network fetchers. The fallback files the bytes under the
 * blob's own disk-cache key (`blossom:<sha256>`), not the original URL's: Coil answers a second
 * request under the key that just failed with that same failure, without asking the mirror.
 */
class BlossomFallbackFetcher(
    private val url: String,
    private val blob: BlossomBlobName,
    private val resolve: suspend (blossomUri: String) -> String?,
    private val primary: () -> Fetcher,
    private val fallback: (url: String) -> Fetcher,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val failure =
            try {
                return primary().fetch()
            } catch (e: Exception) {
                if (e is CancellationException || !isNotHere(e)) throw e
                e
            }

        val uri = blossomUriFor(blob, BlossomAuthorHints.authorsOf(blob.sha256))
        val other =
            try {
                resolve(uri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Could not look for another server for $url", e)
                null
            }

        if (other == null || other == url) throw failure

        Log.d(TAG) { "$url failed (${describe(failure)}); loading the same blob from $other" }

        return try {
            fallback(other).fetch()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "$other failed too", e)
            failure.addSuppressed(e)
            throw failure
        }
    }

    companion object {
        private const val TAG = "BlossomFallbackFetcher"

        /** Whether [e] says the blob is not available from this server, so another may have it. */
        fun isNotHere(e: Throwable): Boolean =
            when (e) {
                is HttpException -> e.response.code.let { it == 404 || it == 410 || it in 500..599 }
                is IOException -> true
                else -> false
            }

        /**
         * The `blossom:` URI the resolver searches with: no server hint (the URL's own server
         * just failed), and the [authors] so their BUD-03 lists are read.
         */
        fun blossomUriFor(
            blob: BlossomBlobName,
            authors: Set<String>,
        ): String =
            BlossomUri(
                sha256 = blob.sha256,
                extension = blob.extension ?: "bin",
                servers = emptyList(),
                authors = authors.toList(),
                size = null,
            ).toUriString()

        private fun describe(e: Exception) = if (e is HttpException) "HTTP ${e.response.code}" else e.javaClass.simpleName
    }
}
