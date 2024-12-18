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
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.ammolite.service.HttpClientManager
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.net.URI
import java.net.URL

class ServerInfoRetriever {
    fun parse(
        baseUrl: String,
        body: String,
    ): ServerInfo {
        val mapper =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val serverInfo = mapper.readValue(body, ServerInfo::class.java)

        return serverInfo.copy(
            apiUrl = makeAbsoluteIfRelativeUrl(baseUrl, serverInfo.apiUrl),
            downloadUrl = serverInfo.downloadUrl?.let { makeAbsoluteIfRelativeUrl(baseUrl, it) },
            delegatedToUrl = serverInfo.delegatedToUrl?.let { makeAbsoluteIfRelativeUrl(baseUrl, it) },
            tosUrl = serverInfo.tosUrl?.let { makeAbsoluteIfRelativeUrl(baseUrl, it) },
            plans =
                serverInfo.plans.mapValues { u ->
                    u.value.copy(
                        url = u.value.url?.let { makeAbsoluteIfRelativeUrl(baseUrl, it) },
                    )
                },
        )
    }

    suspend fun loadInfo(
        baseUrl: String,
        forceProxy: Boolean,
    ): ServerInfo {
        checkNotInMainThread()

        val request: Request =
            Request
                .Builder()
                .header("Accept", "application/nostr+json")
                .url(baseUrl.removeSuffix("/") + "/.well-known/nostr/nip96.json")
                .build()

        HttpClientManager.getHttpClient(forceProxy).newCall(request).execute().use { response ->
            checkNotInMainThread()
            response.use {
                val body = it.body.string()
                try {
                    if (it.isSuccessful) {
                        return parse(baseUrl, body)
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

    fun makeAbsoluteIfRelativeUrl(
        baseUrl: String,
        potentialyRelativeUrl: String,
    ): String =
        try {
            val apiUrl = URI(potentialyRelativeUrl)
            if (apiUrl.isAbsolute) {
                potentialyRelativeUrl
            } else {
                URL(URL(baseUrl), potentialyRelativeUrl).toString()
            }
        } catch (e: Exception) {
            potentialyRelativeUrl
        }
}
