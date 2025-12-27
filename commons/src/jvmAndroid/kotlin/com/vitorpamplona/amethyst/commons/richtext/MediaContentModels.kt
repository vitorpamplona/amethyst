/**
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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import java.io.File

@Immutable
abstract class BaseMediaContent(
    val description: String? = null,
    val dim: DimensionTag? = null,
    val blurhash: String? = null,
)

@Immutable
abstract class MediaUrlContent(
    val url: String,
    description: String? = null,
    val hash: String? = null,
    dim: DimensionTag? = null,
    blurhash: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
) : BaseMediaContent(description, dim, blurhash)

@Immutable
open class MediaUrlImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    val contentWarning: String? = null,
    mimeType: String? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri, mimeType)

class EncryptedMediaUrlImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    contentWarning: String? = null,
    mimeType: String? = null,
    val encryptionAlgo: String,
    val encryptionKey: ByteArray,
    val encryptionNonce: ByteArray,
) : MediaUrlImage(url, description, hash, blurhash, dim, uri, contentWarning, mimeType)

@Immutable
open class MediaUrlVideo(
    url: String,
    description: String? = null,
    hash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    val artworkUri: String? = null,
    val authorName: String? = null,
    blurhash: String? = null,
    val contentWarning: String? = null,
    mimeType: String? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri, mimeType)

@Immutable
class EncryptedMediaUrlVideo(
    url: String,
    description: String? = null,
    hash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    blurhash: String? = null,
    contentWarning: String? = null,
    mimeType: String? = null,
    val encryptionAlgo: String,
    val encryptionKey: ByteArray,
    val encryptionNonce: ByteArray,
) : MediaUrlVideo(url, description, hash, dim, uri, artworkUri, authorName, blurhash, contentWarning, mimeType)

@Immutable
abstract class MediaPreloadedContent(
    val localFile: File?,
    description: String? = null,
    val mimeType: String? = null,
    val isVerified: Boolean? = null,
    dim: DimensionTag? = null,
    blurhash: String? = null,
    val uri: String,
    val id: String? = null,
) : BaseMediaContent(description, dim, blurhash) {
    fun localFileExists() = localFile != null && localFile.exists()
}

@Immutable
class MediaLocalImage(
    localFile: File?,
    mimeType: String? = null,
    description: String? = null,
    dim: DimensionTag? = null,
    blurhash: String? = null,
    isVerified: Boolean? = null,
    uri: String,
) : MediaPreloadedContent(localFile, description, mimeType, isVerified, dim, blurhash, uri)

@Immutable
class MediaLocalVideo(
    localFile: File?,
    mimeType: String? = null,
    description: String? = null,
    dim: DimensionTag? = null,
    blurhash: String? = null,
    isVerified: Boolean? = null,
    uri: String,
    val artworkUri: String? = null,
    val authorName: String? = null,
) : MediaPreloadedContent(localFile, description, mimeType, isVerified, dim, blurhash, uri)
