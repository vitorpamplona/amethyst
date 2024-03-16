/**
 * Copyright (c) 2024 Vitor Pamplona
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
import java.io.File

@Immutable
abstract class BaseMediaContent(
    val description: String? = null,
    val dim: String? = null,
    val blurhash: String? = null,
)

@Immutable
abstract class MediaUrlContent(
    val url: String,
    description: String? = null,
    val hash: String? = null,
    dim: String? = null,
    blurhash: String? = null,
    val uri: String? = null,
) : BaseMediaContent(description, dim, blurhash)

@Immutable
class MediaUrlImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    blurhash: String? = null,
    dim: String? = null,
    uri: String? = null,
    val contentWarning: String? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri)

@Immutable
class MediaUrlVideo(
    url: String,
    description: String? = null,
    hash: String? = null,
    dim: String? = null,
    uri: String? = null,
    val artworkUri: String? = null,
    val authorName: String? = null,
    blurhash: String? = null,
    val contentWarning: String? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri)

@Immutable
abstract class MediaPreloadedContent(
    val localFile: File?,
    description: String? = null,
    val mimeType: String? = null,
    val isVerified: Boolean? = null,
    dim: String? = null,
    blurhash: String? = null,
    val uri: String,
) : BaseMediaContent(description, dim, blurhash) {
    fun localFileExists() = localFile != null && localFile.exists()
}

@Immutable
class MediaLocalImage(
    localFile: File?,
    mimeType: String? = null,
    description: String? = null,
    dim: String? = null,
    blurhash: String? = null,
    isVerified: Boolean? = null,
    uri: String,
) : MediaPreloadedContent(localFile, description, mimeType, isVerified, dim, blurhash, uri)

@Immutable
class MediaLocalVideo(
    localFile: File?,
    mimeType: String? = null,
    description: String? = null,
    dim: String? = null,
    blurhash: String? = null,
    isVerified: Boolean? = null,
    uri: String,
    val artworkUri: String? = null,
    val authorName: String? = null,
) : MediaPreloadedContent(localFile, description, mimeType, isVerified, dim, blurhash, uri)
