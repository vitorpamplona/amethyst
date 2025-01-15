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
package com.vitorpamplona.quartz.nip17Dm

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip14Subject.SubjectTagSerializer
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupSerializer
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.ZapRaiserSerializer
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    ChatroomKeyable,
    NIP17Group {
    /** Recipients intended to receive this conversation */
    fun recipientsPubKey() = tags.mapNotNull { if (it.size > 1 && it[0] == "p") it[1] else null }

    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun talkingWith(oneSideHex: String): Set<HexKey> {
        val listedPubKeys = recipientsPubKey()

        val result =
            if (pubKey == oneSideHex) {
                listedPubKeys.toSet().minus(oneSideHex)
            } else {
                listedPubKeys.plus(pubKey).toSet().minus(oneSideHex)
            }

        if (result.isEmpty()) {
            // talking to myself
            return setOf(pubKey)
        }

        return result
    }

    override fun groupMembers() = recipientsPubKey().plus(pubKey).toSet()

    override fun chatroomKey(toRemove: String): ChatroomKey = ChatroomKey(talkingWith(toRemove).toImmutableSet())

    companion object {
        const val KIND = 14
        const val ALT = "Direct message"

        fun create(
            msg: String,
            to: List<String>? = null,
            subject: String? = null,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            geohash: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            isDraft: Boolean,
            onReady: (ChatMessageEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            to?.forEach { tags.add(arrayOf("p", it)) }
            replyTos?.forEach { tags.add(arrayOf("e", it, "", "reply")) }
            mentions?.forEach { tags.add(arrayOf("p", it, "", "mention")) }
            zapReceiver?.forEach { tags.add(ZapSplitSetupSerializer.toTagArray(it)) }
            zapRaiserAmount?.let { tags.add(ZapRaiserSerializer.toTagArray(it)) }

            if (markAsSensitive) {
                tags.add(ContentWarningSerializer.toTagArray())
            }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            subject?.let { tags.add(SubjectTagSerializer.toTagArray(it)) }
            imetas?.forEach {
                tags.add(Nip92MediaAttachments.createTag(it))
            }
            emojis?.forEach { tags.add(it.toTagArray()) }
            // tags.add(AltTagSerializer.toTagArray(ALT))

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            }
        }
    }
}
