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
package com.vitorpamplona.quartz.nip96FileStorage.info

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URL

class ServerInfoParser {
    fun assembleUrl(apiUrl: String): String = apiUrl.removeSuffix("/") + "/.well-known/nostr/nip96.json"

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
