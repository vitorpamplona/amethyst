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
package com.vitorpamplona.quartz.experimental.profileGallery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Deprecated("Replaced by NIP-68")
@Immutable
class GalleryListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 10011
        const val ALT = "Profile Gallery"
        const val GALLERY_TAG_NAME = "url"

        suspend fun addEvent(
            earlierVersion: GalleryListEvent?,
            eventId: HexKey,
            url: String,
            relay: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = addTag(earlierVersion, GALLERY_TAG_NAME, eventId, url, relay, signer, createdAt)

        suspend fun addTag(
            earlierVersion: GalleryListEvent?,
            tagName: String,
            eventId: HexKey,
            url: String,
            relay: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GalleryListEvent {
            val tags = arrayOf(tagName, url, eventId)
            if (relay != null) {
                tags + relay
            }

            return add(
                earlierVersion,
                arrayOf(tags),
                signer,
                createdAt,
            )
        }

        suspend fun add(
            earlierVersion: GalleryListEvent?,
            listNewTags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GalleryListEvent =
            create(
                content = earlierVersion?.content ?: "",
                tags = listNewTags.plus(earlierVersion?.tags ?: arrayOf()),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun removeEvent(
            earlierVersion: GalleryListEvent,
            eventId: HexKey,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = removeTag(earlierVersion, GALLERY_TAG_NAME, eventId, url, signer, createdAt)

        suspend fun removeReplaceable(
            earlierVersion: GalleryListEvent,
            aTag: ATag,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = removeTag(earlierVersion, GALLERY_TAG_NAME, aTag.toTag(), url, signer, createdAt)

        private suspend fun removeTag(
            earlierVersion: GalleryListEvent,
            tagName: String,
            eventId: HexKey,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GalleryListEvent =
            create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags
                        .filter { it.size <= 1 || !(it[0] == tagName && it[1] == url && it[2] == eventId) }
                        .toTypedArray(),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GalleryListEvent {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }
    }

    @Immutable
    data class GalleryUrl(
        val url: String,
        val id: String,
        val relay: String?,
    ) {
        fun encode(): String = ":$url:$id:$relay"

        companion object {
            fun decode(encodedGallerySetup: String): GalleryUrl? {
                val galleryParts = encodedGallerySetup.split(":", limit = 3)
                return if (galleryParts.size > 3) {
                    GalleryUrl(galleryParts[1], galleryParts[2], galleryParts[3])
                } else {
                    null
                }
            }
        }
    }
}
