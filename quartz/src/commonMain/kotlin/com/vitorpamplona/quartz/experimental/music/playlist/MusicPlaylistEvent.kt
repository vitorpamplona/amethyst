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
package com.vitorpamplona.quartz.experimental.music.playlist

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.music.playlist.tags.CollaborativeTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.DescriptionTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.ImageTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.PrivateTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.PublicTag
import com.vitorpamplona.quartz.experimental.music.playlist.tags.TitleTag
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.builder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class MusicPlaylistEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    /**
     * `isPrivate` wins when both flags are absent or contradictory — a published playlist with
     * `private=true` should never be reported as public. Only fall back to "public by default"
     * when `private` is not asserted.
     */
    fun isPrivate() = tags.firstNotNullOfOrNull(PrivateTag::parse) ?: false

    fun isPublic() = tags.firstNotNullOfOrNull(PublicTag::parse) ?: !isPrivate()

    fun isCollaborative() = tags.firstNotNullOfOrNull(CollaborativeTag::parse) ?: false

    /**
     * Track references as `a` tags pointing to MusicTrackEvent (kind 36787) addressable events.
     * Order is preserved as it appears in the tag array.
     */
    fun trackAddresses(): List<Address> =
        tags.mapNotNull { tag ->
            val address = ATag.parseAddress(tag) ?: return@mapNotNull null
            if (address.kind == MusicTrackEvent.KIND) address else null
        }

    override fun indexableContent(): String =
        buildString {
            append("title: ").append(title().orEmpty()).append('\n')
            description()?.let { append("description: ").append(it).append('\n') }
            append(content)
        }

    companion object {
        const val KIND = 34139
        const val ALT_DESCRIPTION_PREFIX = "Playlist"
        const val CATEGORY_TAG = "playlist"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            /** Long-form description, written into `event.content` (the JSON `content` field). */
            content: String = "",
            image: String? = null,
            /** Short description, written into the `description` tag. */
            description: String? = null,
            tracks: List<Address> = emptyList(),
            isPrivate: Boolean = false,
            isCollaborative: Boolean = false,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MusicPlaylistEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(dTag)
            alt("$ALT_DESCRIPTION_PREFIX: $title")

            title(title)
            hashtag(CATEGORY_TAG)

            image?.let { image(it) }
            description?.let { description(it) }

            tracks.forEach { trackAddress(it) }

            // Always emit exactly one of public/private so clients reading either flag agree on
            // visibility. Without this, an unrelated client that only checks `public` defaults
            // to "true", contradicting a `private=true` on the same event.
            if (isPrivate) {
                private(true)
            } else {
                public(true)
            }
            if (isCollaborative) collaborative(true)

            initializer()
        }

        /**
         * Returns a replacement event with `trackAddress` appended to the end of the track list,
         * preserving every other tag of `earlierVersion` (extra `t` genre tags, custom metadata,
         * description, image, etc). If the track is already present, returns the earlier
         * version's template unchanged.
         */
        fun addTrack(
            earlierVersion: MusicPlaylistEvent,
            trackAddress: Address,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<MusicPlaylistEvent> {
            val aValue = AddressSerializer.assemble(trackAddress.kind, trackAddress.pubKeyHex, trackAddress.dTag)
            val alreadyPresent = earlierVersion.tags.any { it.size > 1 && it[0] == ATag.TAG_NAME && it[1] == aValue }
            val newTags =
                earlierVersion.tags.builder<MusicPlaylistEvent> {
                    if (!alreadyPresent) {
                        // `add` (not `addUnique`) so multiple a-tags accumulate in order.
                        add(arrayOf(ATag.TAG_NAME, aValue))
                    }
                }
            return EventTemplate(createdAt, KIND, newTags, earlierVersion.content)
        }

        /**
         * Returns a replacement event with every `a` tag pointing at `trackAddress` removed.
         * Preserves every other tag of `earlierVersion`. If the track wasn't present, the new
         * tags equal the old tags.
         */
        fun removeTrack(
            earlierVersion: MusicPlaylistEvent,
            trackAddress: Address,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<MusicPlaylistEvent> {
            val aValue = AddressSerializer.assemble(trackAddress.kind, trackAddress.pubKeyHex, trackAddress.dTag)
            val newTags =
                earlierVersion.tags.builder<MusicPlaylistEvent> {
                    remove(ATag.TAG_NAME, aValue)
                }
            return EventTemplate(createdAt, KIND, newTags, earlierVersion.content)
        }
    }
}
