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
package com.vitorpamplona.amethyst.service.uploads.nip96

import android.util.Log
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.quartz.nip96FileStorage.ServerInfo
import com.vitorpamplona.quartz.nip96FileStorage.ServerInfoParser
import kotlinx.coroutines.CancellationException
import okhttp3.Request

class ServerInfoRetriever {
    val parser = ServerInfoParser()

    suspend fun loadInfo(
        baseUrl: String,
        forceProxy: Boolean,
    ): ServerInfo {
        val request: Request =
            Request
                .Builder()
                .header("Accept", "application/nostr+json")
                .url(parser.assembleUrl(baseUrl))
                .build()

        HttpClientManager.getHttpClient(forceProxy).newCall(request).execute().use { response ->
            checkNotInMainThread()
            response.use {
                val body = it.body.string()
                try {
                    if (it.isSuccessful) {
                        return parser.parse(baseUrl, body)
                    } else {
                        throw RuntimeException(
                            "Resulting Message from $baseUrl is an error: ${response.code} ${response.message}",
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("RelayInfoFail", "Resulting Message from $baseUrl in not parseable: $body", e)
                    throw e
                }
            }
        }
    }
}
