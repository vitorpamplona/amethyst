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
package com.vitorpamplona.quartz.nipXXPodcasting20.episode

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.AudioTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.ChaptersTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.DescriptionTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.DurationTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.EditTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.EpisodeNumberTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.ImageTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.PubDateTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.SeasonTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.TitleTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.TranscriptTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.ValueTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.VideoTag
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.podcasts.PodcastEpisode
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Podcasting-2.0 draft podcast episode (`kind:30054`), as published by clients like
 * derekross/podstr. Unlike NIP-F4 (where the podcast is its own keypair and episodes
 * are regular `kind:54` events), here the **human creator** is the keypair and each
 * episode is an *addressable* event keyed by its `d` tag, so it can be edited in place.
 *
 * Implements the spec-neutral [PodcastEpisode] so it lands in the same merged
 * episode list as NIP-F4 episodes.
 */
@Immutable
class Podcasting20EpisodeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PodcastEpisode,
    RootScope,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), description(), content).joinToString("\n")

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun audios() = tags.mapNotNull(AudioTag::parse)

    fun video() = tags.firstNotNullOfOrNull(VideoTag::parse)

    fun number() = tags.firstNotNullOfOrNull(EpisodeNumberTag::parse)

    fun season() = tags.firstNotNullOfOrNull(SeasonTag::parse)

    fun transcriptUrl() = tags.firstNotNullOfOrNull(TranscriptTag::parse)

    fun chaptersUrl() = tags.firstNotNullOfOrNull(ChaptersTag::parse)

    fun value() = tags.firstNotNullOfOrNull(ValueTag::parse)

    fun durationInSeconds() = tags.firstNotNullOfOrNull(DurationTag::parse)

    /** RFC2822 publication date string, kept verbatim for RSS generation. */
    fun pubDate() = tags.firstNotNullOfOrNull(PubDateTag::parse)

    fun alt() = tags.firstNotNullOfOrNull(AltTag::parse)

    fun topics() = hashtags()

    /** Event id of the original publication when this is an edit, if present. */
    fun editsEventId() = tags.firstNotNullOfOrNull(EditTag::parse)

    override fun episodeTitle() = title()

    override fun episodeImage() = image()

    override fun episodeDescription() = description()

    override fun episodeAudio() = audios()

    override fun episodeDurationInSeconds() = durationInSeconds()

    override fun episodePublishedAt() = createdAt

    override fun episodeVideo() = video()

    override fun episodeNumber() = number()

    override fun episodeSeason() = season()

    override fun episodeTranscriptUrl() = transcriptUrl()

    override fun episodeChaptersUrl() = chaptersUrl()

    override fun episodeValue() = value()

    companion object {
        const val KIND = 30054

        fun build(
            dTag: String,
            title: String,
            audios: List<PodcastAudio>,
            pubdate: String,
            alt: String = "Podcast episode: $title",
            description: String? = null,
            image: String? = null,
            durationInSeconds: Long? = null,
            video: PodcastAudio? = null,
            episodeNumber: Int? = null,
            season: Int? = null,
            transcriptUrl: String? = null,
            chaptersUrl: String? = null,
            value: PodcastValue? = null,
            topics: List<String> = emptyList(),
            markdownContent: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Podcasting20EpisodeEvent>.() -> Unit = {},
        ) = eventTemplate<Podcasting20EpisodeEvent>(KIND, markdownContent, createdAt) {
            dTag(dTag)
            title(title)
            audios.forEach { audio(it) }
            pubdate(pubdate)
            alt(alt)

            description?.let { description(it) }
            image?.let { image(it) }
            durationInSeconds?.let { duration(it) }
            video?.let { video(it) }
            episodeNumber?.let { episodeNumber(it) }
            season?.let { season(it) }
            transcriptUrl?.let { transcript(it) }
            chaptersUrl?.let { chapters(it) }
            value?.let { value(it) }
            if (topics.isNotEmpty()) hashtags(topics)

            initializer()
        }
    }
}
