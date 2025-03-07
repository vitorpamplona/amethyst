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

import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
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
import com.vitorpamplona.quartz.nip94FileMetadata.tags.TorrentInfoHash

/**
 * Contains the IMeta tags that are used by Video events.
 */
fun IMetaTag.magnet() = properties.get(MagnetTag.TAG_NAME)

fun IMetaTag.mimeType() = properties.get(MimeTypeTag.TAG_NAME)

fun IMetaTag.alt() = properties.get(AltTag.TAG_NAME)

fun IMetaTag.hash() = properties.get(HashSha256Tag.TAG_NAME)

fun IMetaTag.size() = properties.get(SizeTag.TAG_NAME)

fun IMetaTag.dims() = properties.get(DimensionTag.TAG_NAME)

fun IMetaTag.blurhash() = properties.get(BlurhashTag.TAG_NAME)

fun IMetaTag.originalHash() = properties.get(OriginalHashTag.TAG_NAME)

fun IMetaTag.torrent() = properties.get(TorrentInfoHash.TAG_NAME)

fun IMetaTag.sensitiveContent() = properties.get(ContentWarningTag.TAG_NAME)

fun IMetaTag.image() = properties.get(ImageTag.TAG_NAME)

fun IMetaTag.thumb() = properties.get(ThumbTag.TAG_NAME)

fun IMetaTag.summary() = properties.get(SummaryTag.TAG_NAME)

fun IMetaTag.fallback() = properties.get(FallbackTag.TAG_NAME)

fun IMetaTag.service() = properties.get(ServiceTag.TAG_NAME)
