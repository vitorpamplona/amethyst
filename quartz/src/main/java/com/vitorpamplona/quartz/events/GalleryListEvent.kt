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
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GalleryListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 10011
        const val ALT = "Gallery List"
        const val DEFAULT_D_TAG_GALLERY = "gallery"

        fun addEvent(
            earlierVersion: GalleryListEvent?,
            eventId: HexKey,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) = addTag(earlierVersion, "g", eventId, url, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: GalleryListEvent?,
            tagName: String,
            tagValue: HexKey,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            add(
                earlierVersion,
                arrayOf(arrayOf(tagName, url, tagValue)),
                signer,
                createdAt,
                onReady,
            )
        }

        fun add(
            earlierVersion: GalleryListEvent?,
            listNewTags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            create(
                content = earlierVersion?.content ?: "",
                tags = (earlierVersion?.tags ?: arrayOf(arrayOf("d", DEFAULT_D_TAG_GALLERY))).plus(listNewTags),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun removeEvent(
            earlierVersion: GalleryListEvent,
            eventId: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) = removeTag(earlierVersion, "e", eventId, signer, createdAt, onReady)

        fun removeReplaceable(
            earlierVersion: GalleryListEvent,
            aTag: ATag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) = removeTag(earlierVersion, "a", aTag.toTag(), signer, createdAt, onReady)

        private fun removeTag(
            earlierVersion: GalleryListEvent,
            tagName: String,
            tagValue: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags
                        .filter { it.size <= 1 || !(it[0] == tagName && it[1] == tagValue) }
                        .toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + arrayOf("alt", ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }

        fun create(
            name: String = "",
            images: List<String>? = null,
            videos: List<String>? = null,
            audios: List<String>? = null,
            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("d", name))

            images?.forEach { tags.add(arrayOf("g", it)) }
            videos?.forEach { tags.add(arrayOf("g", it)) }
            audios?.forEach { tags.add(arrayOf("g", it)) }
            tags.add(arrayOf("alt", ALT))

            createPrivateTags(privEvents, privUsers, privAddresses, signer) { content ->
                signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
            }
        }
    }
}
