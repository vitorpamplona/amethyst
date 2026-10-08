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
package com.vitorpamplona.amethyst.service.uploads.nip96

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.ServerInfoRetriever
import com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculator
import com.vitorpamplona.quartz.nip96FileStorage.info.ServerInfo
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import okhttp3.OkHttpClient

/** Android's extension lookup, ahead of the shared client's own table. */
fun androidExtensionFor(mimeType: String): String? = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

private fun ContentResolver.querySize(uri: Uri) =
    query(uri, null, null, null, null)?.use {
        it.moveToFirst()
        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
        it.getLong(sizeIndex)
    }

private fun fileSize(uri: Uri) = runCatching { uri.toFile().length() }.getOrNull()

/** Uploads a content or file [uri] to the NIP-96 server at [serverBaseUrl]. */
suspend fun Nip96Uploader.upload(
    uri: Uri,
    contentType: String?,
    size: Long?,
    alt: String?,
    sensitiveContent: String?,
    serverBaseUrl: String,
    okHttpClient: (String) -> OkHttpClient,
    onProgress: (percentage: Float) -> Unit,
    httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
    context: Context,
): MediaUploadResult =
    upload(
        uri,
        contentType,
        size,
        alt,
        sensitiveContent,
        ServerInfoRetriever().loadInfo(serverBaseUrl, okHttpClient),
        okHttpClient,
        onProgress,
        httpAuth,
        context,
    )

/**
 * Uploads a content or file [uri] to [server], adding the placeholder hashes (blurhash, thumbhash,
 * dimensions) computed locally to what the server reports.
 */
suspend fun Nip96Uploader.upload(
    uri: Uri,
    contentType: String?,
    size: Long?,
    alt: String?,
    sensitiveContent: String?,
    server: ServerInfo,
    okHttpClient: (String) -> OkHttpClient,
    onProgress: (percentage: Float) -> Unit,
    httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
    context: Context,
): MediaUploadResult {
    val contentResolver = context.contentResolver
    val myContentType = contentType ?: contentResolver.getType(uri)
    val length = size ?: contentResolver.querySize(uri) ?: fileSize(uri) ?: 0

    val localMetadata = PreviewMetadataCalculator.computeFromUri(context, uri, myContentType)
    val imageInputStream = contentResolver.openInputStream(uri)

    checkNotNull(imageInputStream) { "Can't open the image input stream" }

    return imageInputStream
        .use { stream ->
            upload(
                inputStream = stream,
                length = length,
                contentType = myContentType,
                alt = alt,
                sensitiveContent = sensitiveContent,
                server = server,
                okHttpClient = okHttpClient,
                onProgress = onProgress,
                httpAuth = httpAuth,
                extensionFor = ::androidExtensionFor,
            )
        }.mergeLocalMetadata(localMetadata)
}
