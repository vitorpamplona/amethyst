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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.ktor

import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Fetcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class KtorNip05Fetcher(
    val httpClient: HttpClient,
) : Nip05Fetcher {
    // NIP-05 requires ignoring HTTP redirects, so we create a dedicated client with redirects disabled.
    private val noRedirectClient =
        HttpClient(httpClient.engine) {
            install(HttpRedirect) {
                checkHttpMethod = false
                allowHttpsDowngrade = false
            }
            followRedirects = false
        }

    override suspend fun fetch(url: String): String {
        val response = noRedirectClient.get(url)

        if (response.status.isSuccess()) {
            return response.bodyAsText()
        } else {
            throw IllegalStateException("Error: ${response.status.value}, ${response.status.description}")
        }
    }
}
