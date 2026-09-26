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
package com.vitorpamplona.amethyst.cli.stores

import com.vitorpamplona.amethyst.commons.cordn.CordnBlobStore
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * A Blossom store for `amy`, over plain OkHttp.
 *
 * Exists because the app's `BlossomUploader` needs an Android `Context` for a
 * MIME lookup that a migration blob does not need — every document is opaque
 * bytes. Keeping a JVM implementation means the whole handoff is drivable and
 * testable without a phone, which is how the interop scripts exercise it.
 *
 * ## The ephemeral signer is the point, not a detail
 *
 * §12 requires the BUD-01 authorization event to be signed by an ephemeral key
 * and never the owner. Signing as the owner would publish, in the clear to the
 * storage server, that this account is uploading right now — the linkage the
 * opaque tip exists to prevent. [signer] is minted per instance and is not
 * derived from the account.
 */
class HttpCordnBlobStore(
    private val servers: List<String>,
    private val http: OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build(),
) : CordnBlobStore {
    private val signer = NostrSignerInternal(KeyPair())

    override suspend fun put(blob: ByteArray): List<String> =
        withContext(Dispatchers.IO) {
            val hash = sha256(blob).toHexKey()
            servers.filter { server -> runCatching { upload(server, blob, hash) }.getOrDefault(false) }
        }

    override suspend fun get(
        address: String,
        servers: List<String>,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            // Ordered: §6 says the reader tries the tip's servers in the order
            // it listed them, most reliable first.
            servers.firstNotNullOfOrNull { server ->
                runCatching {
                    http
                        .newCall(
                            Request
                                .Builder()
                                .url("${server.trimEnd('/')}/$address")
                                .get()
                                .build(),
                        ).execute()
                        .use { if (it.isSuccessful) it.body?.bytes() else null }
                }.getOrNull()
            }
        }

    private suspend fun upload(
        server: String,
        blob: ByteArray,
        hash: String,
    ): Boolean {
        val auth =
            BlossomAuthorizationEvent.createUploadAuth(
                hash = hash,
                size = blob.size.toLong(),
                alt = "",
                signer = signer,
            )

        val request =
            Request
                .Builder()
                .url("${server.trimEnd('/')}/upload")
                .header("Authorization", "Nostr " + Base64.getEncoder().encodeToString(auth.toJson().encodeToByteArray()))
                .put(blob.toRequestBody(null))
                .build()

        return http.newCall(request).execute().use { it.isSuccessful }
    }

    companion object {
        private const val CALL_TIMEOUT_SECONDS = 60L
    }
}
