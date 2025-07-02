/**
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
package com.vitorpamplona.amethyst.service

import android.util.Log
import android.util.LruCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object Nip11CachedRetriever {
    open class RetrieveResult(
        val time: Long,
    )

    class RetrieveResultError(
        val error: Nip11Retriever.ErrorCode,
        val msg: String? = null,
    ) : RetrieveResult(TimeUtils.now())

    class RetrieveResultSuccess(
        val data: Nip11RelayInformation,
    ) : RetrieveResult(TimeUtils.now())

    class RetrieveResultLoading : RetrieveResult(TimeUtils.now())

    private val relayInformationDocumentCache = LruCache<NormalizedRelayUrl, RetrieveResult?>(100)
    private val retriever = Nip11Retriever()

    fun getFromCache(relay: NormalizedRelayUrl): Nip11RelayInformation? {
        val result = relayInformationDocumentCache.get(relay) ?: return null
        if (result is RetrieveResultSuccess) return result.data
        return null
    }

    suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        okHttpClient: (String) -> OkHttpClient,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        checkNotInMainThread()
        val doc = relayInformationDocumentCache.get(relay)
        if (doc != null) {
            if (doc is RetrieveResultSuccess) {
                onInfo(doc.data)
            } else if (doc is RetrieveResultLoading) {
                if (TimeUtils.now() - doc.time < TimeUtils.ONE_MINUTE) {
                    // just wait.
                } else {
                    retrieve(relay, okHttpClient, onInfo, onError)
                }
            } else if (doc is RetrieveResultError) {
                if (TimeUtils.now() - doc.time < TimeUtils.ONE_HOUR) {
                    onError(relay, doc.error, null)
                } else {
                    retrieve(relay, okHttpClient, onInfo, onError)
                }
            }
        } else {
            retrieve(relay, okHttpClient, onInfo, onError)
        }
    }

    private suspend fun retrieve(
        relay: NormalizedRelayUrl,
        okHttpClient: (String) -> OkHttpClient,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        relayInformationDocumentCache.put(relay, RetrieveResultLoading())
        retriever.loadRelayInfo(
            relay = relay,
            okHttpClient = okHttpClient,
            onInfo = {
                relayInformationDocumentCache.put(relay, RetrieveResultSuccess(it))
                onInfo(it)
            },
            onError = { relay, code, errorMsg ->
                relayInformationDocumentCache.put(relay, RetrieveResultError(code, errorMsg))
                onError(relay, code, errorMsg)
            },
        )
    }
}

class Nip11Retriever {
    enum class ErrorCode {
        FAIL_TO_ASSEMBLE_URL,
        FAIL_TO_REACH_SERVER,
        FAIL_TO_PARSE_RESULT,
        FAIL_WITH_HTTP_STATUS,
    }

    suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        okHttpClient: (String) -> OkHttpClient,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, ErrorCode, String?) -> Unit,
    ) {
        checkNotInMainThread()
        val url = relay.toHttp()
        try {
            val request: Request =
                Request
                    .Builder()
                    .header("Accept", "application/nostr+json")
                    .url(url)
                    .build()

            okHttpClient(url)
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
                                        onInfo(Nip11RelayInformation.fromJson(body))
                                    } else {
                                        onError(relay, ErrorCode.FAIL_WITH_HTTP_STATUS, it.code.toString())
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(
                                        "RelayInfoFail",
                                        "Resulting Message from Relay ${relay.url} in not parseable: $body",
                                        e,
                                    )
                                    onError(relay, ErrorCode.FAIL_TO_PARSE_RESULT, e.message)
                                }
                            }
                        }

                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            Log.e("RelayInfoFail", "${relay.url} unavailable", e)
                            onError(relay, ErrorCode.FAIL_TO_REACH_SERVER, e.message)
                        }
                    },
                )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayInfoFail", "Invalid URL ${relay.url}", e)
            onError(relay, ErrorCode.FAIL_TO_ASSEMBLE_URL, e.message)
        }
    }
}
