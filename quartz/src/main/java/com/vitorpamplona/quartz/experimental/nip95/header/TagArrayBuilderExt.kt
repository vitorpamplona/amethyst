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
package com.vitorpamplona.quartz.experimental.nip95.header

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.FallbackTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MagnetTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ServiceTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SummaryTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.TorrentInfoHash

fun TagArrayBuilder<FileStorageHeaderEvent>.mimeType(mimeType: String) = add(MimeTypeTag.assemble(mimeType))

fun TagArrayBuilder<FileStorageHeaderEvent>.hash(hash: HexKey) = add(HashSha256Tag.assemble(hash))

fun TagArrayBuilder<FileStorageHeaderEvent>.fileSize(size: Int) = add(SizeTag.assemble(size))

fun TagArrayBuilder<FileStorageHeaderEvent>.dimension(dim: DimensionTag) = add(DimensionTag.assemble(dim))

fun TagArrayBuilder<FileStorageHeaderEvent>.blurhash(blurhash: String) = add(BlurhashTag.assemble(blurhash))

fun TagArrayBuilder<FileStorageHeaderEvent>.torrentInfohash(hash: String) = add(TorrentInfoHash.assemble(hash))

fun TagArrayBuilder<FileStorageHeaderEvent>.magnet(magnetUri: String) = add(MagnetTag.assemble(magnetUri))

fun TagArrayBuilder<FileStorageHeaderEvent>.image(imageUrl: HexKey) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<FileStorageHeaderEvent>.thumb(trumbUrl: HexKey) = add(ThumbTag.assemble(trumbUrl))

fun TagArrayBuilder<FileStorageHeaderEvent>.summary(summary: HexKey) = add(SummaryTag.assemble(summary))

fun TagArrayBuilder<FileStorageHeaderEvent>.fallback(fallbackUrl: HexKey) = add(FallbackTag.assemble(fallbackUrl))

fun TagArrayBuilder<FileStorageHeaderEvent>.service(service: HexKey) = add(ServiceTag.assemble(service))
