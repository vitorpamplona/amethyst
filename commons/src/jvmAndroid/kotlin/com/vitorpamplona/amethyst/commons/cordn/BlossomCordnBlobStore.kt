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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CancellationException

/**
 * [CordnBlobStore] over plain Blossom servers. Uploads are signed by a throwaway key, so the server
 * learns a size and a hash and nothing about who stored it or what it is.
 */
class BlossomCordnBlobStore(
    private val servers: List<String>,
    private val clientFor: (serverBaseUrl: String) -> BlossomClient,
) : CordnBlobStore {
    private val signer = NostrSignerInternal(KeyPair())

    override suspend fun put(blob: ByteArray): List<String> {
        val hash = sha256(blob).toHexKey()
        val auth = BlossomAuthorizationEvent.createUploadAuth(hash, blob.size.toLong(), "", signer).toAuthorizationHeader()

        return servers.filter { server ->
            try {
                clientFor(server).upload(blob, OPAQUE, server, auth).url != null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        }
    }

    // In the order given: the reader tries the tip's servers as listed, most reliable first.
    override suspend fun get(
        address: String,
        servers: List<String>,
    ): ByteArray? =
        servers.firstNotNullOfOrNull { server ->
            try {
                clientFor(server).download("${server.trimEnd('/')}/$address")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    companion object {
        private const val OPAQUE = "application/octet-stream"
    }
}
