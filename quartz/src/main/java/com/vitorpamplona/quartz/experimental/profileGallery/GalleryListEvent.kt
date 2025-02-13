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
package com.vitorpamplona.quartz.experimental.profileGallery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.GeneralListEvent
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
        const val ALT = "Profile Gallery"
        const val GALLERYTAGNAME = "url"

        fun addEvent(
            earlierVersion: GalleryListEvent?,
            eventId: HexKey,
            url: String,
            relay: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) = addTag(earlierVersion, GALLERYTAGNAME, eventId, url, relay, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: GalleryListEvent?,
            tagName: String,
            eventid: HexKey,
            url: String,
            relay: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            val tags = arrayOf(tagName, url, eventid)
            if (relay != null) {
                tags + relay
            }

            add(
                earlierVersion,
                arrayOf(tags),
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
                tags = listNewTags.plus(earlierVersion?.tags ?: arrayOf()),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun removeEvent(
            earlierVersion: GalleryListEvent,
            eventId: HexKey,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) = removeTag(earlierVersion, GALLERYTAGNAME, eventId, url, signer, createdAt, onReady)

        fun removeReplaceable(
            earlierVersion: GalleryListEvent,
            aTag: ATag,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) = removeTag(earlierVersion, GALLERYTAGNAME, aTag.toTag(), url, signer, createdAt, onReady)

        private fun removeTag(
            earlierVersion: GalleryListEvent,
            tagName: String,
            eventid: HexKey,
            url: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GalleryListEvent) -> Unit,
        ) {
            create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags
                        .filter { it.size <= 1 || !(it[0] == tagName && it[1] == url && it[2] == eventid) }
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
                    tags + AltTag.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
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
