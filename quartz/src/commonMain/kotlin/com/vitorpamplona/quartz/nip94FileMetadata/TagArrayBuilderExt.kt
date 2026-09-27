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
package com.vitorpamplona.quartz.nip94FileMetadata

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.FallbackTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MagnetTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.OriginalHashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ServiceTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SummaryTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.TorrentInfoHash
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag

fun TagArrayBuilder<FileMetadataEvent>.url(url: String) = add(UrlTag.assemble(url))

fun TagArrayBuilder<FileMetadataEvent>.mimeType(mimeType: String) = add(MimeTypeTag.assemble(mimeType))

fun TagArrayBuilder<FileMetadataEvent>.hash(hash: HexKey) = add(HashSha256Tag.assemble(hash))

fun TagArrayBuilder<FileMetadataEvent>.fileSize(size: Int) = add(SizeTag.assemble(size))

fun TagArrayBuilder<FileMetadataEvent>.dimension(dim: DimensionTag) = add(DimensionTag.assemble(dim))

fun TagArrayBuilder<FileMetadataEvent>.blurhash(blurhash: String) = add(BlurhashTag.assemble(blurhash))

fun TagArrayBuilder<FileMetadataEvent>.thumbhash(thumbhash: String) = add(ThumbhashTag.assemble(thumbhash))

fun TagArrayBuilder<FileMetadataEvent>.originalHash(hash: HexKey) = add(OriginalHashTag.assemble(hash))

fun TagArrayBuilder<FileMetadataEvent>.torrentInfohash(hash: String) = add(TorrentInfoHash.assemble(hash))

fun TagArrayBuilder<FileMetadataEvent>.magnet(magnetUri: String) = add(MagnetTag.assemble(magnetUri))

fun TagArrayBuilder<FileMetadataEvent>.image(imageUrl: HexKey) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<FileMetadataEvent>.thumb(trumbUrl: HexKey) = add(ThumbTag.assemble(trumbUrl))

fun TagArrayBuilder<FileMetadataEvent>.summary(summary: HexKey) = add(SummaryTag.assemble(summary))

fun TagArrayBuilder<FileMetadataEvent>.fallback(fallbackUrl: HexKey) = add(FallbackTag.assemble(fallbackUrl))

fun TagArrayBuilder<FileMetadataEvent>.service(service: HexKey) = add(ServiceTag.assemble(service))
