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
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadResult
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/**
 * NAP-UPLOAD's `blossom` rail for a logged-in [account]: the bytes go to the first server on the
 * account's Blossom list (kind 10063), authorized by a kind 24242 upload event the account signs.
 * The napplet never sees the server, the credentials or the signer.
 *
 * [client] gives the HTTP client for an upload to a URL, so the app's Tor choice for uploads
 * applies. A success is reported only for a blob the server stored under the hash of the bytes sent.
 */
class AccountNappletUploadGateway(
    private val account: Account,
    private val client: (url: String) -> OkHttpClient,
    override val maxBytes: Long = NappletUploadGateway.DEFAULT_MAX_BYTES,
) : NappletUploadGateway {
    private fun server(): String? =
        account.blossomServers
            .getBlossomServersList()
            ?.servers()
            ?.firstOrNull()

    override suspend fun hasServer(): Boolean = server() != null

    override suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        filename: String?,
    ): NappletUploadResult? {
        val server = server() ?: return null
        val hash = sha256(bytes).toHexKey()
        return try {
            val auth = account.createBlossomUploadAuth(hash, bytes.size.toLong(), filename ?: "Upload $hash").toAuthorizationHeader()
            val stored = BlossomClient(client(server)).upload(bytes, contentType, server, auth)
            val url = stored.url ?: return null
            // A server that stored something else (or transformed it) did not store these bytes.
            if (stored.sha256 != null && stored.sha256 != hash) {
                Log.w(TAG) { "$server stored ${stored.sha256} for an upload of $hash" }
                return null
            }
            NappletUploadResult(url, hash, stored.size ?: bytes.size.toLong(), stored.type ?: contentType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Upload to $server failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "NappletUpload"
    }
}
