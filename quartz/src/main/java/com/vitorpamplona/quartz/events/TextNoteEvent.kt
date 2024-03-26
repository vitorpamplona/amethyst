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
package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip92MediaAttachments
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseTextNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun root() = tags.firstOrNull { it.size > 3 && it[3] == "root" }?.get(1)

    companion object {
        const val KIND = 1

        fun create(
            msg: String,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            addresses: List<ATag>? = null,
            extraTags: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            replyingTo: String? = null,
            root: String? = null,
            directMentions: Set<HexKey> = emptySet(),
            geohash: String? = null,
            nip94attachments: List<FileHeaderEvent>? = null,
            forkedFrom: Event? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            isDraft: Boolean,
            onReady: (TextNoteEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            replyTos?.let {
                tags.addAll(
                    it.positionalMarkedTags(
                        tagName = "e",
                        root = root,
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
                            root = root,
                            replyingTo = replyingTo,
                            directMentions = directMentions,
                            forkedFrom = (forkedFrom as? AddressableEvent)?.address()?.toTag(),
                        ),
                    )
                }
            findHashtags(msg).forEach {
                val lowercaseTag = it.lowercase()
                tags.add(arrayOf("t", it))
                if (it != lowercaseTag) {
                    tags.add(arrayOf("t", it.lowercase()))
                }
            }
            extraTags?.forEach { tags.add(arrayOf("t", it)) }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            findURLs(msg).forEach { tags.add(arrayOf("r", it)) }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            nip94attachments?.let {
                it.forEach {
                    Nip92MediaAttachments().convertFromFileHeader(it)?.let {
                        tags.add(it)
                    }
                }
            }

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            }
        }
    }
}

fun findURLs(text: String): List<String> {
    return UrlDetector(text, UrlDetectorOptions.Default).detect().map { it.originalUrl }
}

/**
 * Returns a list of NIP-10 marked tags that are also ordered at best effort to support the
 * deprecated method of positional tags to maximize backwards compatibility with clients that
 * support replies but have not been updated to understand tag markers.
 *
 * https://github.com/nostr-protocol/nips/blob/master/10.md
 *
 * The tag to the root of the reply chain goes first. The tag to the reply event being responded
 * to goes last. The order for any other tag does not matter, so keep the relative order.
 */
fun List<String>.positionalMarkedTags(
    tagName: String,
    root: String?,
    replyingTo: String?,
    directMentions: Set<HexKey>,
    forkedFrom: String?,
) = sortedWith { o1, o2 ->
    when {
        o1 == o2 -> 0
        o1 == root -> -1 // root goes first
        o2 == root -> 1 // root goes first
        o1 == replyingTo -> 1 // reply event being responded to goes last
        o2 == replyingTo -> -1 // reply event being responded to goes last
        else -> 0 // keep the relative order for any other tag
    }
}
    .map {
        when (it) {
            root -> arrayOf(tagName, it, "", "root")
            replyingTo -> arrayOf(tagName, it, "", "reply")
            forkedFrom -> arrayOf(tagName, it, "", "fork")
            in directMentions -> arrayOf(tagName, it, "", "mention")
            else -> arrayOf(tagName, it)
        }
    }
