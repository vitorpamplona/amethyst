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
package com.vitorpamplona.quartz.podcasts

/**
 * Spec-neutral view of a single podcast episode.
 *
 * Two competing podcast drafts publish episodes with incompatible identity models
 * and event kinds:
 *  - **NIP-F4** ([com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent],
 *    `kind:54`): a regular event where the *podcast itself* is a Nostr keypair.
 *  - **Podcasting-2.0 draft** ([com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent],
 *    `kind:30054`): an addressable event where the *human creator* is the keypair
 *    and episodes are editable via their `d` tag.
 *
 * The two cannot share a single wire kind, but a client can still render them in
 * one list. This interface is that merge point: feeds and UI depend on it instead
 * of a concrete event, so both kinds flow into the same podcast/episode list.
 */
interface PodcastEpisode {
    /** The episode title shown in listings. */
    fun episodeTitle(): String?

    /** Cover/episode artwork URL, if any. */
    fun episodeImage(): String?

    /** Short, plain-text episode description/summary, if any. */
    fun episodeDescription(): String?

    /**
     * One or more audio sources for the episode. Multiple entries typically offer
     * the same audio in different containers/codecs (e.g. mp3 + opus).
     */
    fun episodeAudio(): List<PodcastAudio>

    /** Episode duration in seconds, if the publisher provided it. */
    fun episodeDurationInSeconds(): Long?

    /**
     * Unix timestamp (seconds) used to order episodes in a merged feed. Both drafts
     * fall back to the event's `created_at`; the Podcasting-2.0 draft additionally
     * carries an RFC2822 `pubdate` tag exposed by its own event class.
     */
    fun episodePublishedAt(): Long

    /**
     * A video source for the episode, if it ships one. NIP-F4 has no video tag and returns null;
     * the Podcasting-2.0 draft carries a `video` tag.
     */
    fun episodeVideo(): PodcastAudio? = null

    /** Episode number within the show/season, if provided. */
    fun episodeNumber(): Int? = null

    /** Season number the episode belongs to, if provided. */
    fun episodeSeason(): Int? = null

    /** URL of an off-event transcript document, if provided. */
    fun episodeTranscriptUrl(): String? = null

    /** URL of an off-event Podcasting-2.0 chapters document, if provided. */
    fun episodeChaptersUrl(): String? = null
}
