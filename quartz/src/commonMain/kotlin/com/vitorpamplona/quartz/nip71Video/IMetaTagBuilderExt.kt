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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip71Video.tags.BitrateTag
import com.vitorpamplona.quartz.nip71Video.tags.DurationTag
import com.vitorpamplona.quartz.nip71Video.tags.LanguageImetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
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
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.WaveformTag

/**
 * Contains the IMeta tags that are used by Video events.
 */
fun IMetaTagBuilder.magnet(uri: String) = add(MagnetTag.TAG_NAME, uri)

fun IMetaTagBuilder.mimeType(mime: String) = add(MimeTypeTag.TAG_NAME, mime)

fun IMetaTagBuilder.alt(alt: String) = add(AltTag.TAG_NAME, alt)

fun IMetaTagBuilder.hash(hash: HexKey) = add(HashSha256Tag.TAG_NAME, hash)

fun IMetaTagBuilder.size(size: Int) = add(SizeTag.TAG_NAME, size.toString())

fun IMetaTagBuilder.dims(dims: DimensionTag) = add(DimensionTag.TAG_NAME, dims.toString())

fun IMetaTagBuilder.blurhash(blurhash: String) = add(BlurhashTag.TAG_NAME, blurhash)

fun IMetaTagBuilder.thumbhash(thumbhash: String) = add(ThumbhashTag.TAG_NAME, thumbhash)

fun IMetaTagBuilder.originalHash(originalHash: String) = add(OriginalHashTag.TAG_NAME, originalHash)

fun IMetaTagBuilder.torrent(uri: String) = add(TorrentInfoHash.TAG_NAME, uri)

fun IMetaTagBuilder.sensitiveContent(reason: String) = add(ContentWarningTag.TAG_NAME, reason)

fun IMetaTagBuilder.image(imageUrl: HexKey) = add(ImageTag.TAG_NAME, imageUrl)

fun IMetaTagBuilder.thumb(thumbUrl: HexKey) = add(ThumbTag.TAG_NAME, thumbUrl)

fun IMetaTagBuilder.summary(summary: HexKey) = add(SummaryTag.TAG_NAME, summary)

fun IMetaTagBuilder.fallback(fallback: HexKey) = add(FallbackTag.TAG_NAME, fallback)

fun IMetaTagBuilder.service(service: HexKey) = add(ServiceTag.TAG_NAME, service)

fun IMetaTagBuilder.bitrate(bitsPerSecond: Int) = add(BitrateTag.TAG_NAME, bitsPerSecond.toString())

fun IMetaTagBuilder.duration(seconds: Double) = add(DurationTag.TAG_NAME, seconds.toString())

fun IMetaTagBuilder.duration(seconds: Int) = add(DurationTag.TAG_NAME, seconds.toString())

fun IMetaTagBuilder.waveform(wave: List<Float>) = add(WaveformTag.TAG_NAME, WaveformTag.assembleWaveFloat(wave))

fun IMetaTagBuilder.language(language: LanguageImetaTag) = add(LanguageImetaTag.TAG_NAME, language.toPropertyValue())

fun IMetaTagBuilder.language(
    code: String,
    standard: String = LanguageImetaTag.DEFAULT_STANDARD,
    originalVersion: Boolean = false,
) = language(LanguageImetaTag(code, standard, originalVersion))
