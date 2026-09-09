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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip25Reactions.tags.ExternalTargetTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emoji
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A reaction to something that is not a nostr event (kind 17) — NIP-25.
 *
 * "If the target of a reaction is not a native nostr event, the reaction MUST be a `kind 17`
 * event and MUST include NIP-73 external content `k` + `i` tags." So a like on a web page, a
 * podcast episode, a book or a paper lands here rather than in [ReactionEvent], and it names its
 * target by external id instead of by event id.
 *
 * A single reaction may carry several `k`/`i` pairs — a podcast reaction names both the show and
 * the episode — so the accessors return lists rather than pretending there is one target.
 */
@Immutable
class ExternalReactionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The NIP-73 ids this reaction is aimed at, in tag order. */
    fun externalIds(): List<String> = tags.mapNotNull(ExternalTargetTag::parse)

    /** The NIP-73 kinds (`web`, `podcast:item:guid`, `isbn`, ...) declared alongside the ids. */
    fun externalKinds(): List<String> = tags.mapNotNull(ReplyKindTag::parse)

    /** The first id that is openable as a link, preferring an explicit hint over the id itself. */
    fun openableUrl(): String? =
        tags.firstNotNullOfOrNull(ExternalTargetTag::parseHint)
            ?: externalIds().firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    fun isLike() = content == ReactionEvent.LIKE || content.isEmpty()

    fun isDislike() = content == ReactionEvent.DISLIKE

    /** True when this reaction can be attributed to a target at all. */
    fun hasTarget() = externalIds().isNotEmpty()

    companion object {
        const val KIND = 17

        fun build(
            reaction: String,
            target: ExternalId,
            hint: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExternalReactionEvent>.() -> Unit = {},
        ): EventTemplate<ExternalReactionEvent> =
            eventTemplate(KIND, reaction, createdAt) {
                // The spec pairs each id with the kind that says how to read it; publishing one
                // without the other leaves a reader unable to tell a URL from a GUID.
                add(ReplyKindTag.assemble(target.toKind()))
                add(ExternalTargetTag.assemble(target.toScope(), hint))

                initializer()
            }

        fun build(
            reaction: EmojiUrlTag,
            target: ExternalId,
            hint: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExternalReactionEvent>.() -> Unit = {},
        ): EventTemplate<ExternalReactionEvent> =
            build(reaction.toContentEncode(), target, hint, createdAt) {
                emoji(reaction)
                initializer()
            }
    }
}
