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
package com.vitorpamplona.amethyst.service.uploads.hls

import android.content.Context
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType

/**
 * Turns a user-chosen [ServerName] into an [HlsBlobUploader] by adapting the concrete
 * [Nip96Uploader] / [BlossomUploader] to the simpler file+contentType interface the HLS upload
 * pipeline uses. Keeps the pipeline free of direct Amethyst/account wiring so it stays
 * unit-testable.
 */
object HlsBlobUploaderFactory {
    fun create(
        server: ServerName,
        account: Account,
        context: Context,
    ): HlsBlobUploader =
        when (server.type) {
            ServerType.Blossom -> {
                blossomAdapter(server.baseUrl, account, context)
            }

            ServerType.NIP96 -> {
                nip96Adapter(server.baseUrl, account, context)
            }

            ServerType.NIP95 -> {
                throw IllegalArgumentException(
                    "NIP-95 storage stores each blob as an event and is not suitable for HLS renditions",
                )
            }
        }

    private fun blossomAdapter(
        serverBaseUrl: String,
        account: Account,
        context: Context,
    ): HlsBlobUploader =
        HlsBlobUploader { file, contentType ->
            BlossomUploader().upload(
                uri = file.toUri(),
                contentType = contentType,
                size = file.length(),
                alt = null,
                sensitiveContent = null,
                serverBaseUrl = serverBaseUrl,
                okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                httpAuth = account::createBlossomUploadAuth,
                context = context,
            )
        }

    private fun nip96Adapter(
        serverBaseUrl: String,
        account: Account,
        context: Context,
    ): HlsBlobUploader =
        HlsBlobUploader { file, contentType ->
            Nip96Uploader().upload(
                uri = file.toUri(),
                contentType = contentType,
                size = file.length(),
                alt = null,
                sensitiveContent = null,
                serverBaseUrl = serverBaseUrl,
                okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                onProgress = { /* pipeline reports progress per-upload; NIP-96 per-request progress is not forwarded */ },
                httpAuth = account::createHTTPAuthorization,
                context = context,
            )
        }
}
