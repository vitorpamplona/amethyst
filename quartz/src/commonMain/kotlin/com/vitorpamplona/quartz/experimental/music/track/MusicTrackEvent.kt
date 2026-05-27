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
package com.vitorpamplona.quartz.experimental.music.track

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.music.track.tags.AlbumTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ArtistTag
import com.vitorpamplona.quartz.experimental.music.track.tags.BitrateTag
import com.vitorpamplona.quartz.experimental.music.track.tags.DurationTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ExplicitTag
import com.vitorpamplona.quartz.experimental.music.track.tags.FormatTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ImageTag
import com.vitorpamplona.quartz.experimental.music.track.tags.LanguageTag
import com.vitorpamplona.quartz.experimental.music.track.tags.ReleasedTag
import com.vitorpamplona.quartz.experimental.music.track.tags.SampleRateTag
import com.vitorpamplona.quartz.experimental.music.track.tags.TitleTag
import com.vitorpamplona.quartz.experimental.music.track.tags.TrackNumberTag
import com.vitorpamplona.quartz.experimental.music.track.tags.UrlTag
import com.vitorpamplona.quartz.experimental.music.track.tags.VideoUrlTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class MusicTrackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun artist() = tags.firstNotNullOfOrNull(ArtistTag::parse)

    fun url() = tags.firstNotNullOfOrNull(UrlTag::parse)

    fun videoUrl() = tags.firstNotNullOfOrNull(VideoUrlTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun album() = tags.firstNotNullOfOrNull(AlbumTag::parse)

    fun trackNumber() = tags.firstNotNullOfOrNull(TrackNumberTag::parse)

    fun released() = tags.firstNotNullOfOrNull(ReleasedTag::parse)

    fun duration() = tags.firstNotNullOfOrNull(DurationTag::parse)

    fun format() = tags.firstNotNullOfOrNull(FormatTag::parse)

    fun bitrate() = tags.firstNotNullOfOrNull(BitrateTag::parse)

    fun sampleRate() = tags.firstNotNullOfOrNull(SampleRateTag::parse)

    fun language() = tags.firstNotNullOfOrNull(LanguageTag::parse)

    fun isExplicit() = tags.firstNotNullOfOrNull(ExplicitTag::parse) ?: false

    companion object {
        const val KIND = 36787
        const val ALT_DESCRIPTION_PREFIX = "Music track"
        const val GENRE_TAG = "music"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            artist: String,
            url: String,
            description: String = "",
            image: String? = null,
            videoUrl: String? = null,
            album: String? = null,
            trackNumber: Int? = null,
            released: String? = null,
            duration: Int? = null,
            format: String? = null,
            bitrate: String? = null,
            sampleRate: Int? = null,
            language: String? = null,
            explicit: Boolean = false,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MusicTrackEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            alt("$ALT_DESCRIPTION_PREFIX: $title by $artist")

            title(title)
            artist(artist)
            url(url)
            hashtag(GENRE_TAG)

            image?.let { image(it) }
            videoUrl?.let { videoUrl(it) }
            album?.let { album(it) }
            trackNumber?.let { trackNumber(it) }
            released?.let { released(it) }
            duration?.let { duration(it) }
            format?.let { format(it) }
            bitrate?.let { bitrate(it) }
            sampleRate?.let { sampleRate(it) }
            language?.let { language(it) }
            if (explicit) explicit(true)

            initializer()
        }
    }
}
