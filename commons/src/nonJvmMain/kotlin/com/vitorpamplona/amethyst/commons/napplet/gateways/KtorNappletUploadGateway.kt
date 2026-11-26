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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadGateway
import com.vitorpamplona.amethyst.commons.service.upload.KtorBlossomClient
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import io.ktor.client.HttpClient

/**
 * NAP-UPLOAD's `blossom` rail for a logged-in [account] off the JVM ([AccountNappletBlossomGateway]):
 * the same uploads as the app's own media, on [KtorBlossomClient], with [client] giving the client for
 * an upload to a URL (the uploads role), so the app's Tor choice for uploads applies.
 */
class KtorNappletUploadGateway(
    account: Account,
    client: (url: String) -> HttpClient,
    maxBytes: Long = NappletUploadGateway.DEFAULT_MAX_BYTES,
) : NappletUploadGateway by AccountNappletBlossomGateway(account, { KtorBlossomClient(client).asNappletRail() }, maxBytes)

/** This client's upload and holds-check, as a napplet upload's [NappletBlossomRail]. */
fun KtorBlossomClient.asNappletRail(): NappletBlossomRail =
    object : NappletBlossomRail {
        override suspend fun upload(
            bytes: ByteArray,
            contentType: String,
            serverBaseUrl: String,
            authHeader: String,
        ): BlossomUploadResult = this@asNappletRail.upload(bytes, contentType, serverBaseUrl, authHeader)

        override suspend fun has(
            hash: HexKey,
            serverBaseUrl: String,
        ): Boolean = this@asNappletRail.has(hash, serverBaseUrl)
    }
