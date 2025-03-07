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

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip71Video.tags.DurationTag
import com.vitorpamplona.quartz.nip71Video.tags.SegmentTag
import com.vitorpamplona.quartz.nip71Video.tags.TextTrackTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

fun <T : VideoEvent> TagArrayBuilder<T>.title(title: String) = addUnique(TitleTag.assemble(title))

fun <T : VideoEvent> TagArrayBuilder<T>.publishedAt(timestamp: Long) = addUnique(PublishedAtTag.assemble(timestamp))

fun <T : VideoEvent> TagArrayBuilder<T>.duration(durationInSeconds: Int) = addUnique(DurationTag.assemble(durationInSeconds))

fun <T : VideoEvent> TagArrayBuilder<T>.videoIMeta(
    url: String,
    mimeType: String? = null,
    blurhash: String? = null,
    dimension: DimensionTag? = null,
    hash: String? = null,
    size: Int? = null,
    alt: String? = null,
) = videoIMeta(VideoMeta(url, mimeType, blurhash, dimension, alt, hash, size))

fun <T : VideoEvent> TagArrayBuilder<T>.videoIMeta(imeta: VideoMeta) = add(imeta.toIMetaArray())

fun <T : VideoEvent> TagArrayBuilder<T>.videoIMetas(imageUrls: List<VideoMeta>) = addAll(imageUrls.map { it.toIMetaArray() })

fun <T : VideoEvent> TagArrayBuilder<T>.textTrack(track: TextTrackTag) = add(track.toTagArray())

fun <T : VideoEvent> TagArrayBuilder<T>.textTracks(tracks: List<TextTrackTag>) = addAll(tracks.map { it.toTagArray() })

fun <T : VideoEvent> TagArrayBuilder<T>.segment(seg: SegmentTag) = add(seg.toTagArray())

fun <T : VideoEvent> TagArrayBuilder<T>.segments(segs: List<SegmentTag>) = addAll(segs.map { it.toTagArray() })

fun <T : VideoEvent> TagArrayBuilder<T>.participant(key: PTag) = add(key.toTagArray())

fun <T : VideoEvent> TagArrayBuilder<T>.participants(keys: List<PTag>) = addAll(keys.map { it.toTagArray() })
