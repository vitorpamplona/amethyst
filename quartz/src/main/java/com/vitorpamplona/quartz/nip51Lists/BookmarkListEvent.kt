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
package com.vitorpamplona.quartz.nip51Lists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class BookmarkListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30001
        const val ALT = "List of bookmarks"
        const val DEFAULT_D_TAG_BOOKMARKS = "bookmark"

        fun addEvent(
            earlierVersion: BookmarkListEvent?,
            eventId: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) = addTag(earlierVersion, "e", eventId, isPrivate, signer, createdAt, onReady)

        fun addReplaceable(
            earlierVersion: BookmarkListEvent?,
            aTag: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) = addTag(earlierVersion, "a", aTag.toTag(), isPrivate, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: BookmarkListEvent?,
            tagName: String,
            tagValue: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) {
            add(
                earlierVersion,
                arrayOf(arrayOf(tagName, tagValue)),
                isPrivate,
                signer,
                createdAt,
                onReady,
            )
        }

        fun add(
            earlierVersion: BookmarkListEvent?,
            listNewTags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) {
            if (isPrivate) {
                if (earlierVersion != null) {
                    earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                        encryptTags(
                            privateTags = privateTags.plus(listNewTags),
                            signer = signer,
                        ) { encryptedTags ->
                            create(
                                content = encryptedTags,
                                tags = earlierVersion.tags,
                                signer = signer,
                                createdAt = createdAt,
                                onReady = onReady,
                            )
                        }
                    }
                } else {
                    encryptTags(
                        privateTags = listNewTags,
                        signer = signer,
                    ) { encryptedTags ->
                        create(
                            content = encryptedTags,
                            tags = arrayOf(arrayOf("d", DEFAULT_D_TAG_BOOKMARKS)),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady,
                        )
                    }
                }
            } else {
                create(
                    content = earlierVersion?.content ?: "",
                    tags = (earlierVersion?.tags ?: arrayOf(arrayOf("d", DEFAULT_D_TAG_BOOKMARKS))).plus(listNewTags),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady,
                )
            }
        }

        fun removeEvent(
            earlierVersion: BookmarkListEvent,
            eventId: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) = removeTag(earlierVersion, "e", eventId, isPrivate, signer, createdAt, onReady)

        fun removeReplaceable(
            earlierVersion: BookmarkListEvent,
            aTag: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) = removeTag(earlierVersion, "a", aTag.toTag(), isPrivate, signer, createdAt, onReady)

        private fun removeTag(
            earlierVersion: BookmarkListEvent,
            tagName: String,
            tagValue: HexKey,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) {
            if (isPrivate) {
                earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                    encryptTags(
                        privateTags =
                            privateTags
                                .filter { it.size <= 1 || !(it[0] == tagName && it[1] == tagValue) }
                                .toTypedArray(),
                        signer = signer,
                    ) { encryptedTags ->
                        create(
                            content = encryptedTags,
                            tags =
                                earlierVersion.tags
                                    .filter { it.size <= 1 || !(it[0] == tagName && it[1] == tagValue) }
                                    .toTypedArray(),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady,
                        )
                    }
                }
            } else {
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
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }

        fun create(
            name: String = "",
            events: List<String>? = null,
            users: List<String>? = null,
            addresses: List<ATag>? = null,
            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (BookmarkListEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("d", name))

            events?.forEach { tags.add(arrayOf("e", it)) }
            users?.forEach { tags.add(arrayOf("p", it)) }
            addresses?.forEach { tags.add(arrayOf("a", it.toTag())) }
            tags.add(AltTag.assemble(ALT))

            createPrivateTags(privEvents, privUsers, privAddresses, signer) { content ->
                signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
            }
        }
    }
}
