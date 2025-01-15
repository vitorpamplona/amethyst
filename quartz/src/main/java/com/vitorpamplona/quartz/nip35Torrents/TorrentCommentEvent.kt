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
package com.vitorpamplona.quartz.nip35Torrents

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.buildHashtagTags
import com.vitorpamplona.quartz.nip10Notes.BaseTextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.buildUrlRefs
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.positionalMarkedTags
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupSerializer
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.ZapRaiserSerializer
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class TorrentCommentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseTextNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    private fun innerTorrent() =
        tags.firstOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }
            ?: tags.firstOrNull { it.size > 1 && it[0] == "e" }

    fun torrent() = innerTorrent()?.getOrNull(1)

    companion object {
        const val KIND = 2004
        const val ALT = "Comment for a Torrent file"

        fun create(
            message: String,
            torrent: HexKey,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            addresses: List<ATag>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            markAsSensitive: Boolean,
            replyingTo: String? = null,
            directMentions: Set<HexKey> = emptySet(),
            zapRaiserAmount: Long?,
            geohash: String? = null,
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            forkedFrom: Event? = null,
            isDraft: Boolean,
            onReady: (TorrentCommentEvent) -> Unit,
        ) {
            val content = message

            val tags = mutableListOf<Array<String>>()
            replyTos?.let {
                tags.addAll(
                    it.positionalMarkedTags(
                        tagName = "e",
                        root = torrent,
                        replyingTo = replyingTo,
                        directMentions = directMentions,
                        forkedFrom = forkedFrom?.id,
                    ),
                )
            }
            mentions?.forEach {
                if (it in directMentions) {
                    tags.add(arrayOf("p", it, "", "mention"))
                } else {
                    tags.add(arrayOf("p", it))
                }
            }
            replyTos?.forEach {
                if (it in directMentions) {
                    tags.add(arrayOf("q", it))
                }
            }
            addresses
                ?.map { it.toTag() }
                ?.let {
                    tags.addAll(
                        it.positionalMarkedTags(
                            tagName = "a",
                            root = torrent,
                            replyingTo = replyingTo,
                            directMentions = directMentions,
                            forkedFrom = (forkedFrom as? AddressableEvent)?.address()?.toTag(),
                        ),
                    )
                }
            tags.addAll(buildHashtagTags(findHashtags(message)))
            tags.addAll(buildUrlRefs(findURLs(message)))

            zapReceiver?.forEach { tags.add(ZapSplitSetupSerializer.toTagArray(it)) }
            zapRaiserAmount?.let { tags.add(ZapRaiserSerializer.toTagArray(it)) }

            if (markAsSensitive) {
                tags.add(ContentWarningSerializer.toTagArray())
            }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            imetas?.forEach {
                tags.add(Nip92MediaAttachments.createTag(it))
            }
            emojis?.forEach { tags.add(it.toTagArray()) }
            tags.add(AltTagSerializer.toTagArray(ALT))

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), content, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
            }
        }
    }
}
