/**
 * Copyright (c) 2023 Vitor Pamplona
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

import android.util.Log
import android.util.LruCache
import com.vitorpamplona.amethyst.model.RelayInformation
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object Nip11CachedRetriever {
    val relayInformationDocumentCache = LruCache<String, RelayInformation>(100)
    val retriever = Nip11Retriever()

    suspend fun loadRelayInfo(
        dirtyUrl: String,
        onInfo: (RelayInformation) -> Unit,
        onError: (String, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        val url = retriever.cleanUrl(dirtyUrl)
        val doc = relayInformationDocumentCache.get(url)

        if (doc != null) {
            onInfo(doc)
        } else {
            Nip11Retriever()
                .loadRelayInfo(
                    url,
                    dirtyUrl,
                    onInfo = {
                        relayInformationDocumentCache.put(url, it)
                        onInfo(it)
                    },
                    onError,
                )
        }
    }
}

class Nip11Retriever {
    enum class ErrorCode {
        FAIL_TO_ASSEMBLE_URL,
        FAIL_TO_REACH_SERVER,
        FAIL_TO_PARSE_RESULT,
        FAIL_WITH_HTTP_STATUS,
    }

    fun cleanUrl(dirtyUrl: String): String {
        return if (dirtyUrl.contains("://")) {
            dirtyUrl.replace("wss://", "https://").replace("ws://", "http://")
        } else {
            "https://$dirtyUrl"
        }
    }

    suspend fun loadRelayInfo(
        url: String,
        dirtyUrl: String,
        onInfo: (RelayInformation) -> Unit,
        onError: (String, ErrorCode, String?) -> Unit,
    ) {
        try {
            val request: Request =
                Request.Builder().header("Accept", "application/nostr+json").url(url).build()

            HttpClientManager.getHttpClient()
                .newCall(request)
                .enqueue(
                    object : Callback {
                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            checkNotInMainThread()
                            response.use {
                                val body = it.body.string()
                                try {
                                    if (it.isSuccessful) {
                                        onInfo(RelayInformation.fromJson(body))
                                    } else {
                                        onError(dirtyUrl, ErrorCode.FAIL_WITH_HTTP_STATUS, it.code.toString())
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(
                                        "RelayInfoFail",
                                        "Resulting Message from Relay $dirtyUrl in not parseable: $body",
                                        e,
                                    )
                                    onError(dirtyUrl, ErrorCode.FAIL_TO_PARSE_RESULT, e.message)
                                }
                            }
                        }

                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            Log.e("RelayInfoFail", "$dirtyUrl unavailable", e)
                            onError(dirtyUrl, ErrorCode.FAIL_TO_REACH_SERVER, e.message)
                        }
                    },
                )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayInfoFail", "Invalid URL $dirtyUrl", e)
            onError(dirtyUrl, ErrorCode.FAIL_TO_ASSEMBLE_URL, e.message)
        }
    }
}
