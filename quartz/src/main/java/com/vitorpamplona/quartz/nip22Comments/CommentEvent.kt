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
package com.vitorpamplona.quartz.nip22Comments

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

@Immutable
class CommentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    fun rootAuthor() = tags.firstNotNullOfOrNull(RootAuthorTag::parse)

    fun replyAuthor() = tags.firstNotNullOfOrNull(ReplyAuthorTag::parse)

    fun rootAuthors() = tags.filter(RootAuthorTag::match)

    fun replyAuthors() = tags.filter(ReplyAuthorTag::match)

    fun rootScopes() = tags.filter { RootIdentifierTag.match(it) || RootAddressTag.match(it) || RootEventTag.match(it) }

    fun rootKinds() = tags.filter(RootKindTag::match)

    fun directReplies() = tags.filter { ReplyIdentifierTag.match(it) || ReplyAddressTag.match(it) || ReplyEventTag.match(it) }

    fun directKinds() = tags.filter(ReplyKindTag::match)

    fun isGeohashTag(tag: Array<String>) = tag.size > 1 && (tag[0] == "i" || tag[0] == "I") && tag[1].startsWith("geo:")

    private fun getGeoHashList() = tags.filter { isGeohashTag(it) }

    fun hasGeohashes() = tags.any { isGeohashTag(it) }

    fun geohashes() = getGeoHashList().map { it[1].drop(4).lowercase() }

    fun getGeoHash(): String? = geohashes().maxByOrNull { it.length }

    fun isTaggedGeoHash(hashtag: String) = tags.any { isGeohashTag(it) && it[1].endsWith(hashtag, true) }

    fun isTaggedGeoHashes(hashtags: Set<String>) = geohashes().any { it in hashtags }

    override fun markedReplyTos(): List<HexKey> =
        tags.mapNotNull(ReplyEventTag::parseKey) +
            tags.mapNotNull(RootEventTag::parseKey)

    override fun unmarkedReplyTos() = emptyList<String>()

    override fun replyingTo(): HexKey? =
        tags.lastNotNullOfOrNull(ReplyEventTag::parseKey)
            ?: tags.lastNotNullOfOrNull(RootEventTag::parseKey)

    fun replyingToAddressId(): String? =
        tags.lastNotNullOfOrNull(RootAddressTag::parseAddress)
            ?: tags.lastNotNullOfOrNull(ReplyAddressTag::parseAddress)

    override fun replyingToAddressOrEvent(): HexKey? = replyingToAddressId() ?: replyingTo()

    companion object {
        const val KIND = 1111
        const val ALT = "Reply to "

        fun replyBuilder(
            msg: String,
            replyingTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            alt(ALT + replyingTo.toNostrUri())

            if (replyingTo.event is CommentEvent) {
                addAll(replyingTo.event.rootScopes())
                addAll(replyingTo.event.rootKinds())
                addAll(replyingTo.event.rootAuthors())
            } else {
                if (replyingTo.event is AddressableEvent) {
                    rootAddress(replyingTo.event.addressTag(), replyingTo.relay)
                    replyAddress(replyingTo.event.addressTag(), replyingTo.relay)
                }

                rootEvent(replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey)
                rootKind(replyingTo.event.kind)
                rootAuthor(replyingTo.event.pubKey, replyingTo.authorHomeRelay)
            }

            replyEvent(replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey)
            replyKind(replyingTo.event.kind)
            replyAuthor(replyingTo.event.pubKey, replyingTo.authorHomeRelay)

            initializer()
        }

        fun replyExternalIdentity(
            msg: String,
            extId: ExternalId,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            alt(ALT + extId.toScope())

            rootExternalIdentity(extId)
            rootKind(extId)

            replyExternalIdentity(extId)
            replyKind(extId)

            initializer()
        }
    }
}
