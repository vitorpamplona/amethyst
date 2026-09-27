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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import okio.IOException

/**
 * [HttpRelayTransport] over OkHttp. [httpClient] picks the client per relay, as
 * [com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket.Builder] does for
 * websockets, so a .onion relay can go through Tor and a clearnet one directly. OkHttp asks for
 * gzip and inflates it as the body streams, so a relay's sync-flushed gzip arrives line by line.
 */
class OkHttpRelayTransport(
    private val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
) : HttpRelayTransport {
    override suspend fun post(
        relay: NormalizedRelayUrl,
        url: String,
        body: ByteArray,
        authorization: String?,
        onStatus: (status: Int, retryAfter: String?) -> Unit,
        onLine: (String) -> Unit,
    ) = coroutineScope {
        val request =
            Request
                .Builder()
                .url(url)
                .post(body.toRequestBody(JSON))
                .header("Accept", NDJSON)
                .apply { authorization?.let { header("Authorization", it) } }
                .build()
        val call = httpClient(relay).newCall(request)
        // A blocking read does not see coroutine cancellation; cancelling the call unblocks it.
        val watcher =
            launch {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
        try {
            call.executeAsync().use { response ->
                onStatus(response.code, response.header("Retry-After"))
                withContext(Dispatchers.IO) {
                    val source = response.body.source()
                    try {
                        while (true) onLine(source.readUtf8Line() ?: break)
                    } catch (_: IOException) {
                        // The connection dropped mid-answer: what came is what the reader says it is.
                    }
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    companion object {
        const val NDJSON = "application/x-ndjson"
        private val JSON = "application/json".toMediaType()
    }
}
