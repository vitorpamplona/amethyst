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
package com.vitorpamplona.quartz.nip28PublicChat

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip10Notes.BaseTextNoteEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip57Zaps.ZapSplitSetup
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseTextNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    IsInPublicChatChannel {
    override fun channel() =
        tags.firstOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }?.get(1)
            ?: tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    override fun markedReplyTos() = super.markedReplyTos().filter { it != channel() }

    override fun unMarkedReplyTos() = super.unMarkedReplyTos().filter { it != channel() }

    companion object {
        const val KIND = 42
        const val ALT = "Public chat message"

        fun create(
            message: String,
            channel: String,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            directMentions: Set<HexKey> = emptySet(),
            geohash: String? = null,
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            isDraft: Boolean,
            onReady: (ChannelMessageEvent) -> Unit,
        ) {
            val tags =
                mutableListOf(
                    arrayOf("e", channel, "", "root"),
                )
            mentions?.forEach { tags.add(arrayOf("p", it)) }
            replyTos?.forEach {
                if (it in directMentions) {
                    tags.add(arrayOf("q", it))
                } else {
                    tags.add(arrayOf("e", it))
                }
            }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            imetas?.forEach {
                tags.add(Nip92MediaAttachments.createTag(it))
            }
            emojis?.forEach { tags.add(it.toTagArray()) }
            tags.add(
                arrayOf("alt", ALT),
            )

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), message, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), message, onReady)
            }
        }
    }
}

interface IsInPublicChatChannel {
    fun channel(): String?
}
