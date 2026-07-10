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
package com.vitorpamplona.amethyst.model.nip11RelayInfo

import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import okhttp3.OkHttpClient

@Stable
class Nip11CachedRetriever(
    val okHttpClient: (NormalizedRelayUrl) -> OkHttpClient,
) {
    private val relayInformationEmptyCache = LruCache<NormalizedRelayUrl, Nip11RelayInformation>(1000)
    private val relayInformationDocumentCache = LruCache<NormalizedRelayUrl, RetrieveResult?>(1000)
    private val retriever = Nip11Retriever(okHttpClient)

    fun trimToSize(maxItems: Int) {
        relayInformationDocumentCache.trimToSize(maxItems)
        // relayInformationEmptyCache holds only lightweight display-name+favicon-url placeholders;
        // trimming it saves negligible memory but forces redundant NIP-11 HTTP fetches on resume.
    }

    fun getEmpty(relay: NormalizedRelayUrl): Nip11RelayInformation {
        relayInformationEmptyCache.get(relay)?.let { return it }

        val info =
            Nip11RelayInformation(
                name = relay.displayUrl(),
                icon = relay.toHttp() + "favicon.ico",
            )

        relayInformationEmptyCache.put(relay, info)

        return info
    }

    fun getFromCache(relay: NormalizedRelayUrl): Nip11RelayInformation {
        val result = relayInformationDocumentCache.get(relay)

        if (result == null) {
            // resets the clock
            val empty = getEmpty(relay)
            relayInformationDocumentCache.put(relay, RetrieveResult.Empty(empty))
            return empty
        }

        return when (result) {
            is RetrieveResult.Success -> result.data
            is RetrieveResult.Error -> result.data
            is RetrieveResult.Empty -> result.data
            is RetrieveResult.Loading -> result.data
        }
    }

    suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        val doc = relayInformationDocumentCache.get(relay)
        if (doc != null) {
            when (doc) {
                is RetrieveResult.Success -> {
                    onInfo(doc.data)
                }

                is RetrieveResult.Loading -> {
                    // A `Loading` marker means SOME caller started a fetch — but the coroutine that
                    // owns it may already be gone (e.g. the composable that launched the warm-up left
                    // composition and its scope was cancelled mid-fetch), which would leave this marker
                    // stuck and valid for an hour. The old "just wait" here dropped this caller's
                    // callback entirely, so a screen that navigated in on top of an aborted load would
                    // never receive the doc and would render as if the relay had no NIP-11 (no `self`,
                    // no supported_nips) — hiding relay-signed NIP-29 groups until the marker expired.
                    // Re-fetch instead: the fetch is cheap, dedups at the HTTP layer, and guarantees
                    // this caller is notified.
                    retrieve(relay, onInfo, onError)
                }

                is RetrieveResult.Error -> {
                    if (doc.isValid()) {
                        onError(relay, doc.error, null)
                    } else {
                        retrieve(relay, onInfo, onError)
                    }
                }

                is RetrieveResult.Empty -> {
                    retrieve(relay, onInfo, onError)
                }
            }
        } else {
            retrieve(relay, onInfo, onError)
        }
    }

    private suspend fun retrieve(
        relay: NormalizedRelayUrl,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        relayInformationDocumentCache.put(relay, RetrieveResult.Loading(getEmpty(relay)))
        retriever.loadRelayInfo(
            relay = relay,
            onInfo = {
                relayInformationDocumentCache.put(relay, RetrieveResult.Success(it))
                relayInformationEmptyCache.remove(relay)
                onInfo(it)
            },
            onError = { relay, code, errorMsg ->
                relayInformationDocumentCache.put(relay, RetrieveResult.Error(getEmpty(relay), code, errorMsg))
                relayInformationEmptyCache.remove(relay)
                onError(relay, code, errorMsg)
            },
        )
    }
}
