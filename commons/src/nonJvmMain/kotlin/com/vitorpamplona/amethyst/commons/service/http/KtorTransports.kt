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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpResponse
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Fetcher
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpResponse
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraHttpResponse
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraHttpTransport
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * The plain HTTP transports on a Ktor [HttpClient], for the targets without OkHttp. Each mirrors
 * its OkHttp counterpart (`OkHttpNip05Fetcher`, `OkHttpLnurlTransport`, `OkHttpMintTransport`,
 * `OkHttpEsploraTransport`).
 */
class KtorNip05Fetcher(
    private val client: () -> HttpClient,
) : Nip05Fetcher {
    override suspend fun fetch(url: String): String {
        val response = client().get(url)
        if (!response.status.isSuccess()) throw IllegalStateException("NIP-05 fetch of $url failed: ${response.status}")
        return response.bodyAsText()
    }
}

class KtorLnurlTransport(
    private val client: () -> HttpClient,
) : LnurlHttpTransport {
    override suspend fun get(url: String): LnurlHttpResponse {
        val response = client().get(url)
        return LnurlHttpResponse(response.status.value, response.status.description, response.bodyAsText())
    }
}

class KtorMintTransport(
    private val client: () -> HttpClient,
) : MintHttpTransport {
    override suspend fun get(url: String): MintHttpResponse {
        val response = client().get(url)
        return MintHttpResponse(response.status.value, response.bodyAsText())
    }

    override suspend fun postJson(
        url: String,
        json: String,
    ): MintHttpResponse {
        val response =
            client().post(url) {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        return MintHttpResponse(response.status.value, response.bodyAsText())
    }
}

class KtorEsploraTransport(
    private val client: () -> HttpClient,
) : EsploraHttpTransport {
    override suspend fun getJson(url: String): EsploraHttpResponse = client().get(url) { header("Accept", "application/json") }.toEsplora()

    override suspend fun get(url: String): EsploraHttpResponse = client().get(url).toEsplora()

    override suspend fun postText(
        url: String,
        body: String,
    ): EsploraHttpResponse =
        client()
            .post(url) {
                contentType(ContentType.Text.Plain)
                setBody(body)
            }.toEsplora()

    private suspend fun HttpResponse.toEsplora() = EsploraHttpResponse(status.value, status.description, bodyAsText())
}
