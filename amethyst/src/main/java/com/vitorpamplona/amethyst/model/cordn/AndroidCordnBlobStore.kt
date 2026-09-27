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
package com.vitorpamplona.amethyst.model.cordn

import android.content.Context
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.cordn.CordnBlobStore
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * Where a migration's sealed documents are stored, on Android.
 *
 * ## The authorization is signed by a throwaway, and that is the point
 *
 * `multi-device.md` §12 requires the BUD-01 upload authorization to be signed
 * by an ephemeral key and **never** the owner `npub` — the same rule as the
 * tip. Signing as the owner would tell the storage server, in the clear and
 * under the account's own name, that this person is uploading right now. That
 * is precisely the linkage the opaque tip is built to avoid, and it would
 * arrive by a side door.
 *
 * So this does NOT reuse `account.createBlossomUploadAuth` the way
 * [CordnMediaService] does for message attachments. Those are already
 * attributable — they ride inside a group the coordinator can see traffic for
 * — whereas the point of a migration blob is that nothing links it to anyone.
 * [signer] is minted per instance and derived from nothing.
 */
class AndroidCordnBlobStore(
    private val servers: List<String>,
    private val context: Context,
) : CordnBlobStore {
    private val signer = NostrSignerInternal(KeyPair())

    override suspend fun put(blob: ByteArray): List<String> {
        val hash = sha256(blob).toHexKey()

        return servers.filter { server ->
            runCatching {
                BlossomUploader()
                    .upload(
                        inputStream = ByteArrayInputStream(blob),
                        hash = hash,
                        length = blob.size.toLong(),
                        baseFileName = hash,
                        // Opaque on purpose: the server learns a size and a
                        // hash, and nothing about what kind of thing this is.
                        contentType = OPAQUE,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = server,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        httpAuth = { h, size, alt -> BlossomAuthorizationEvent.createUploadAuth(h, size, alt ?: "", signer) },
                        context = context,
                        useMediaEndpoint = false,
                    ).url != null
            }.getOrDefault(false)
        }
    }

    override suspend fun get(
        address: String,
        servers: List<String>,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            // Ordered: §6 has the reader try the tip's servers as listed, most
            // reliable first.
            servers.firstNotNullOfOrNull { server ->
                runCatching {
                    val url = "${server.trimEnd('/')}/$address"
                    Amethyst.instance.roleBasedHttpClientBuilder
                        .okHttpClientForImage(url)
                        .newCall(
                            Request
                                .Builder()
                                .url(url)
                                .get()
                                .build(),
                        ).execute()
                        .use { if (it.isSuccessful) it.body?.bytes() else null }
                }.getOrNull()
            }
        }

    companion object {
        private const val OPAQUE = "application/octet-stream"
    }
}
