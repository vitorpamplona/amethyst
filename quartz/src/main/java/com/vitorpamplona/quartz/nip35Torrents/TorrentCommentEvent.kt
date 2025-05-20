/**
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
package com.vitorpamplona.quartz.nip35Torrents

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTags
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.positionalMarkedTags
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
@Deprecated("Replaced by NIP-22")
class TorrentCommentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint) + tags.mapNotNull(QTag::parseEventAsHint)

    override fun addressHints() = tags.mapNotNull(QTag::parseAddressAsHint)

    fun torrent() = tags.firstNotNullOfOrNull(MarkedETag::parseRoot) ?: tags.firstNotNullOfOrNull(ETag::parse)

    fun torrentIds() = tags.firstNotNullOfOrNull(MarkedETag::parseRootId) ?: tags.firstNotNullOfOrNull(ETag::parseId)

    companion object {
        const val KIND = 2004
        const val ALT_DESCRIPTION = "Comment for a Torrent file"

        fun build(
            message: String,
            torrent: EventHintBundle<TorrentEvent>,
            replyingTo: EventHintBundle<TorrentCommentEvent>?,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<TorrentCommentEvent> {
            val eTags =
                if (replyingTo == null) {
                    listOfNotNull(torrent.toETag())
                } else {
                    replyingTo.event.taggedEvents() + replyingTo.toETag()
                }

            // double check the order and erases older markers.
            val sortedAndMarked =
                eTags.positionalMarkedTags(
                    root = torrent.toETag(),
                    replyingTo = replyingTo?.toETag(),
                    forkedFrom = null,
                )

            return build(message, createdAt) {
                eTags(sortedAndMarked)
            }
        }

        @Deprecated("Replaced by NIP-22")
        fun build(
            post: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TorrentCommentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
