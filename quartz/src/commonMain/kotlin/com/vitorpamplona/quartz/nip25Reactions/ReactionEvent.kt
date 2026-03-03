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
package com.vitorpamplona.quartz.nip25Reactions

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.events.toETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emoji
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ReactionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun originalPost() = tags.mapNotNull(ETag::parseId)

    fun originalAuthor() = tags.mapNotNull(PTag::parseKey)

    companion object {
        const val KIND = 7
        const val LIKE = "+"
        const val DISLIKE = "-"

        fun like(
            reactedTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
        ) = build(LIKE, reactedTo, createdAt)

        fun dislike(
            reactedTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
        ) = build(DISLIKE, reactedTo, createdAt)

        fun build(
            reaction: String,
            reactedTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<ReactionEvent>(KIND, reaction, createdAt) {
            eTag(reactedTo.toETag())
            if (reactedTo.event is AddressableEvent) {
                aTag(reactedTo.event.address(), reactedTo.relay)
            }
            pTag(reactedTo.event.pubKey, reactedTo.relay)
            kind(reactedTo.event.kind)
        }

        fun build(
            reaction: EmojiUrlTag,
            reactedTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<ReactionEvent>(KIND, reaction.toContentEncode(), createdAt) {
            eTag(reactedTo.toETag())
            if (reactedTo.event is AddressableEvent) {
                aTag(reactedTo.event.address(), reactedTo.relay)
            }
            pTag(reactedTo.event.pubKey, reactedTo.relay)
            kind(reactedTo.event.kind)
            emoji(reaction)
        }
    }
}
