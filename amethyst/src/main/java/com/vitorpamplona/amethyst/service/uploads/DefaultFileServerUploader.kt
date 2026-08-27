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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.originless.OriginlessUrls
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.service.uploads.originless.OriginlessUploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType

/**
 * Uploads through the account's default file server. Originless is NIP-96-simple
 * (multipart, no auth) and returns `ipfs://` URLs; NIP-96 and Blossom keep their
 * existing contracts.
 */
object DefaultFileServerUploader {
    suspend fun upload(
        account: Account,
        uri: Uri,
        contentType: String?,
        size: Long?,
        context: Context,
        alt: String? = null,
        sensitiveContent: String? = null,
        onProgress: (percentage: Float) -> Unit = {},
    ): MediaUploadResult {
        val server = account.settings.defaultFileServer
        val okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads
        return when (server.type) {
            ServerType.NIP96 ->
                Nip96Uploader().upload(
                    uri = uri,
                    contentType = contentType,
                    size = size,
                    alt = alt,
                    sensitiveContent = sensitiveContent,
                    serverBaseUrl = server.baseUrl,
                    okHttpClient = okHttpClient,
                    onProgress = onProgress,
                    httpAuth = account::createHTTPAuthorization,
                    context = context,
                )
            ServerType.Originless ->
                OriginlessUploader().uploadToAll(
                    uri = uri,
                    contentType = contentType,
                    size = size,
                    alt = alt,
                    sensitiveContent = sensitiveContent,
                    serverBaseUrls = OriginlessUrls.uploadTargets(account.settings.originlessServerUrls.value),
                    okHttpClient = okHttpClient,
                    onProgress = onProgress,
                    context = context,
                    useMedia = account.settings.optimizeMediaOnUpload.value,
                )
            ServerType.Blossom,
            ServerType.NIP95,
            ->
                BlossomUploader().upload(
                    uri = uri,
                    contentType = contentType,
                    size = size,
                    alt = alt,
                    sensitiveContent = sensitiveContent,
                    serverBaseUrl = server.baseUrl,
                    okHttpClient = okHttpClient,
                    httpAuth = account::createBlossomUploadAuth,
                    context = context,
                )
        }
    }
}
