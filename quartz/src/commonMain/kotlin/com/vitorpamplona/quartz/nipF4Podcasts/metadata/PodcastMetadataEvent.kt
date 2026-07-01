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
package com.vitorpamplona.quartz.nipF4Podcasts.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.DescriptionTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.ImageTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.TitleTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.WebsiteTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-F4 show-level metadata for a podcast. Authored by the podcast's own pubkey
 * (each podcast is its own Nostr keypair). Replaceable: each new event of this
 * kind replaces the previous version for that pubkey.
 *
 * Podcast-specific clients SHOULD read from [PodcastMetadataEvent] directly and
 * ignore the podcast's `kind:0` profile, per spec — the show name and cover art
 * shipped here are the canonical ones for podcast UIs even if the same pubkey
 * also publishes a microblogging profile.
 */
@Immutable
class PodcastMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun websites() = tags.mapNotNull(WebsiteTag::parse)

    /**
     * Returns claimed authors and their roles. The spec warns these claims are
     * unverified — a podcast can name anyone as author. Before surfacing an author
     * to users, cross-check with an [com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent]
     * counter-claim signed by that author's own key.
     */
    fun claimedAuthors() = tags.mapNotNull(AuthorTag::parse)

    companion object {
        const val KIND = 10154

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey) = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun build(
            title: String,
            image: String,
            description: String,
            websites: List<String> = emptyList(),
            authors: List<AuthorTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PodcastMetadataEvent>.() -> Unit = {},
        ) = eventTemplate<PodcastMetadataEvent>(KIND, "", createdAt) {
            title(title)
            image(image)
            description(description)

            websites.forEach { website(it) }
            authors.forEach { author(it) }

            initializer()
        }
    }
}
