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
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.buildHashtagTags
import com.vitorpamplona.quartz.nip10Notes.BaseTextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.ETag
import com.vitorpamplona.quartz.nip10Notes.PTag
import com.vitorpamplona.quartz.nip10Notes.content.buildUrlRefs
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip19Bech32.parseAtag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupSerializer
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.ZapRaiserSerializer
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
class CommentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseTextNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    fun root() = tags.firstOrNull { it.size > 3 && it[3] == "root" }?.get(1)

    fun getRootScopes() = tags.filter { it.size > 1 && it[0] == "I" || it[0] == "A" || it[0] == "E" }

    fun getRootKinds() = tags.filter { it.size > 1 && it[0] == "K" }

    fun getDirectReplies() = tags.filter { it.size > 1 && it[0] == "i" || it[0] == "a" || it[0] == "e" }

    fun getDirectKinds() = tags.filter { it.size > 1 && it[0] == "k" }

    fun isGeohashTag(tag: Array<String>) = tag.size > 1 && (tag[0] == "i" || tag[0] == "I") && tag[1].startsWith("geo:")

    private fun getGeoHashList() = tags.filter { isGeohashTag(it) }

    fun hasGeohashes() = tags.any { isGeohashTag(it) }

    fun geohashes() = getGeoHashList().map { it[1].drop(4).lowercase() }

    fun getGeoHash(): String? = geohashes().maxByOrNull { it.length }

    fun isTaggedGeoHash(hashtag: String) = tags.any { isGeohashTag(it) && it[1].endsWith(hashtag, true) }

    fun isTaggedGeoHashes(hashtags: Set<String>) = geohashes().any { it in hashtags }

    override fun markedReplyTos(): List<HexKey> = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] } + tags.filter { it.size > 1 && it[0] == "E" }.map { it[1] }

    override fun unMarkedReplyTos() = emptyList<String>()

    override fun replyingTo(): HexKey? =
        tags.lastOrNull { it.size > 1 && it[0] == "e" }?.get(1)
            ?: tags.lastOrNull { it.size > 1 && it[0] == "E" }?.get(1)

    override fun replyingToAddress(): ATag? =
        tags.lastOrNull { it.size > 1 && it[0] == "a" }?.let { ATag.parseAtag(it[1], it.getOrNull(2)) }
            ?: tags.lastOrNull { it.size > 1 && it[0] == "A" }?.let { ATag.parseAtag(it[1], it.getOrNull(2)) }

    override fun replyingToAddressOrEvent(): HexKey? = replyingToAddress()?.toTag() ?: replyingTo()

    companion object {
        const val KIND = 1111

        fun rootGeohashMipMap(geohash: String): Array<Array<String>> =
            geohash.indices
                .asSequence()
                .map { arrayOf("I", "geo:" + geohash.substring(0, it + 1)) }
                .toList()
                .reversed()
                .toTypedArray()

        fun firstReplyToEvent(
            msg: String,
            replyingTo: EventHintBundle<Event>,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            geohash: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            isDraft: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommentEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()

            if (replyingTo.event is AddressableEvent) {
                tags.add(removeTrailingNullsAndEmptyOthers("A", replyingTo.event.addressTag(), replyingTo.relay))
                tags.add(removeTrailingNullsAndEmptyOthers("a", replyingTo.event.addressTag(), replyingTo.relay))
            }

            tags.add(removeTrailingNullsAndEmptyOthers("E", replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey))
            tags.add(arrayOf("K", "${replyingTo.event.kind}"))

            tags.add(removeTrailingNullsAndEmptyOthers("e", replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey))
            tags.add(arrayOf("k", "${replyingTo.event.kind}"))

            create(msg, tags, usersMentioned, addressesMentioned, eventsMentioned, imetas, emojis, geohash, zapReceiver, markAsSensitive, zapRaiserAmount, isDraft, signer, createdAt, onReady)
        }

        fun replyComment(
            msg: String,
            replyingTo: EventHintBundle<CommentEvent>,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            geohash: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            isDraft: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommentEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()

            tags.addAll(replyingTo.event.getRootScopes())
            tags.addAll(replyingTo.event.getRootKinds())

            tags.add(removeTrailingNullsAndEmptyOthers("e", replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey))
            tags.add(arrayOf("k", "${replyingTo.event.kind}"))

            create(msg, tags, usersMentioned, addressesMentioned, eventsMentioned, imetas, emojis, geohash, zapReceiver, markAsSensitive, zapRaiserAmount, isDraft, signer, createdAt, onReady)
        }

        fun createGeoComment(
            msg: String,
            geohash: String? = null,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            isDraft: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommentEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            geohash?.let { tags.addAll(rootGeohashMipMap(it)) }
            tags.add(arrayOf("K", "geo"))

            create(msg, tags, usersMentioned, addressesMentioned, eventsMentioned, imetas, emojis, null, zapReceiver, markAsSensitive, zapRaiserAmount, isDraft, signer, createdAt, onReady)
        }

        private fun create(
            msg: String,
            tags: MutableList<Array<String>>,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            geohash: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            isDraft: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommentEvent) -> Unit,
        ) {
            usersMentioned.forEach { tags.add(it.toPTagArray()) }
            addressesMentioned.forEach { tags.add(it.toQTagArray()) }
            eventsMentioned.forEach { tags.add(it.toQTagArray()) }

            tags.addAll(buildHashtagTags(findHashtags(msg)))
            tags.addAll(buildUrlRefs(findURLs(msg)))

            emojis?.forEach { tags.add(it.toTagArray()) }

            zapReceiver?.forEach { tags.add(ZapSplitSetupSerializer.toTagArray(it)) }
            zapRaiserAmount?.let { tags.add(ZapRaiserSerializer.toTagArray(it)) }

            if (markAsSensitive) {
                tags.add(ContentWarningSerializer.toTagArray())
            }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            imetas?.forEach {
                tags.add(Nip92MediaAttachments.createTag(it))
            }

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            }
        }
    }
}
