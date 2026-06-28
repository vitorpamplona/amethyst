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
 * Spec-neutral view of a podcast show (the channel-level metadata), the companion of
 * [PodcastEpisode].
 *
 * Two competing drafts model the show differently:
 *  - **NIP-F4** ([com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent],
 *    `kind:10154`): a dedicated replaceable event whose own pubkey *is* the podcast, with the
 *    show fields in tags.
 *  - **Podcasting-2.0 draft** ([com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata],
 *    a view over a `kind:30078` NIP-78 app-data event with `d="podcast-metadata"`): the creator's
 *    pubkey owns the show and the fields live in a JSON content blob.
 *
 * In both models the show's pubkey is also the author of its episodes, so a single show card and
 * a single per-show episode list serve both. Feeds and UI depend on this interface to merge them.
 */
interface PodcastShow {
    /** The show/podcast name. */
    fun showTitle(): String?

    /** Cover-art URL, if any. */
    fun showImage(): String?

    /** Show description/summary, if any. */
    fun showDescription(): String?

    /** Associated website URLs (possibly empty). */
    fun showWebsites(): List<String>

    /**
     * Free-text author/host byline (not a Nostr pubkey), if the draft carries one. NIP-F4 models
     * authors as pubkeys with roles instead, so it leaves this null.
     */
    fun showAuthor(): String? = null

    /** Genre/category labels (e.g. "Technology"), possibly empty. */
    fun showCategories(): List<String> = emptyList()

    /** Donation/funding page URLs (Podcasting 2.0 `funding`), possibly empty. */
    fun showFundingUrls(): List<String> = emptyList()

    /** Whether the show is flagged as explicit. */
    fun showIsExplicit(): Boolean = false

    /** Whether the show is marked complete/finished (no further episodes expected). */
    fun showIsComplete(): Boolean = false

    /** Copyright line, if provided. */
    fun showCopyright(): String? = null

    /** The show's default value-for-value split block, if any. NIP-F4 has no V4V and returns null. */
    fun showValue(): PodcastValue? = null
}
