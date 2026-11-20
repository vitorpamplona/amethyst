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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
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
 * After such a failure, in order:
 * 1. [cached]: a copy a mirror served before, read from disk without the network, so an image
 *    that was found once still shows offline and after a restart.
 * 2. When [isOnline], [resolve] (the BUD-10 resolver) is asked, for at most [RESOLVE_WAIT_MS],
 *    for a `blossom:` URI that carries the authors [BlossomAuthorHints] knows for the hash, so it
 *    probes their servers (and the viewer's own) with a HEAD each. The resolver keeps going in its
 *    own scope past the wait, so a slow answer is ready for the next load.
 * 3. [fallback] loads it from the server found, and [verify] checks the bytes hash to the name
 *    before they are kept: a mirror is anyone's server, and the copy is filed under the blob's
 *    content key, where every later load of that hash finds it.
 *
 * Anything that finds nothing leaves the original failure in place.
 */
class BlossomFallbackFetcher(
    private val url: String,
    private val blob: BlossomBlobName,
    private val resolve: suspend (blossomUri: String) -> String?,
    private val primary: () -> Fetcher,
    private val fallback: (url: String) -> Fetcher,
    private val cached: suspend () -> FetchResult? = { null },
    private val isOnline: () -> Boolean = { true },
    private val verify: (FetchResult?) -> Boolean = { true },
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val failure =
            try {
                return primary().fetch()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // OkHttp reports a call Coil cancelled mid-read as an IOException: that's the
                // user scrolling away, not a server without the file, so nothing more is asked.
                currentCoroutineContext().ensureActive()
                if (!isNotHere(e)) throw e
                e
            }

        try {
            cached()?.let { return it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not read the cached copy of ${blob.sha256}", e)
        }

        if (!isOnline()) throw failure

        val uri = blossomUriFor(blob, BlossomAuthorHints.authorsOf(blob.sha256))
        val other =
            try {
                withTimeoutOrNull(RESOLVE_WAIT_MS) { resolve(uri) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Could not look for another server for the blob", e)
                null
            }

        if (other == null || other == url) throw failure

        Log.d(TAG) { "$url failed (${describe(failure)}); loading the same blob from $other" }

        val result =
            try {
                fallback(other).fetch()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "The mirror failed too", e)
                failure.addSuppressed(e)
                throw failure
            }

        if (!verify(result)) {
            Log.w(TAG) { "$other served bytes that don't hash to ${blob.sha256}; dropped them" }
            throw failure
        }
        return result
    }

    companion object {
        private const val TAG = "BlossomFallbackFetcher"

        /**
         * How long a failed image waits for another server before its failure shows. The
         * resolver's own limit is longer, and it caches what it finds for the next load.
         */
        const val RESOLVE_WAIT_MS = 6_000L

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
