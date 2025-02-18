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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.FallbackTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MagnetTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.OriginalHashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ServiceTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SummaryTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.TorrentInfoHash

/**
 * Contains the IMeta tags that are used by Video events.
 */
fun IMetaTagBuilder.magnet(uri: String) = add(MagnetTag.TAG_NAME, uri)

fun IMetaTagBuilder.mimeType(mime: String) = add(MimeTypeTag.TAG_NAME, mime)

fun IMetaTagBuilder.alt(alt: String) = add(AltTag.TAG_NAME, alt)

fun IMetaTagBuilder.hash(hash: HexKey) = add(HashTag.TAG_NAME, hash)

fun IMetaTagBuilder.size(size: Int) = add(SizeTag.TAG_NAME, size.toString())

fun IMetaTagBuilder.dims(dims: DimensionTag) = add(DimensionTag.TAG_NAME, dims.toString())

fun IMetaTagBuilder.blurhash(blurhash: String) = add(BlurhashTag.TAG_NAME, blurhash)

fun IMetaTagBuilder.originalHash(originalHash: String) = add(OriginalHashTag.TAG_NAME, originalHash)

fun IMetaTagBuilder.torrent(uri: String) = add(TorrentInfoHash.TAG_NAME, uri)

fun IMetaTagBuilder.sensitiveContent(reason: String) = add(ContentWarningTag.TAG_NAME, reason)

fun IMetaTagBuilder.image(imageUrl: HexKey) = add(ImageTag.TAG_NAME, imageUrl)

fun IMetaTagBuilder.thumb(thumbUrl: HexKey) = add(ThumbTag.TAG_NAME, thumbUrl)

fun IMetaTagBuilder.summary(summary: HexKey) = add(SummaryTag.TAG_NAME, summary)

fun IMetaTagBuilder.fallback(fallback: HexKey) = add(FallbackTag.TAG_NAME, fallback)

fun IMetaTagBuilder.service(service: HexKey) = add(ServiceTag.TAG_NAME, service)
