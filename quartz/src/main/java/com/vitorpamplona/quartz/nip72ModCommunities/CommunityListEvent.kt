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
package com.vitorpamplona.quartz.nip72ModCommunities

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent.Companion.FIXED_D_TAG
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip19Bech32.parseAtag
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip51Lists.GeneralListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class CommunityListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var publicAndPrivateEventCache: ImmutableSet<ATag>? = null

    override fun countMemory(): Long =
        super.countMemory() +
            32 + (publicAndPrivateEventCache?.sumOf { pointerSizeInBytes + it.countMemory() } ?: 0L) // rough calculation

    override fun dTag() = FIXED_D_TAG

    fun publicAndPrivateEvents(
        signer: NostrSigner,
        onReady: (ImmutableSet<ATag>) -> Unit,
    ) {
        publicAndPrivateEventCache?.let { eventList ->
            onReady(eventList)
            return
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateEventCache =
                filterTagList("a", it)
                    .mapNotNull { ATag.parseAtag(it, null) }
                    .toImmutableSet()

            publicAndPrivateEventCache?.let { eventList ->
                onReady(eventList)
            }
        }
    }

    companion object {
        const val KIND = 10004
        const val ALT = "Community List"

        fun blockListFor(pubKeyHex: HexKey): String = "$KIND:$pubKeyHex:"

        fun createListWithTag(
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            if (isPrivate) {
                encryptTags(arrayOf(arrayOf(key, tag)), signer) { encryptedTags ->
                    create(
                        content = encryptedTags,
                        tags = emptyArray(),
                        signer = signer,
                        createdAt = createdAt,
                        onReady = onReady,
                    )
                }
            } else {
                create(
                    content = "",
                    tags = arrayOf(arrayOf(key, tag)),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady,
                )
            }
        }

        fun createListWithEvent(
            address: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = createListWithTag("a", address.toTag(), isPrivate, signer, createdAt, onReady)

        fun addEvents(
            earlierVersion: CommunityListEvent,
            listAddresses: List<ATag>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            if (isPrivate) {
                earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                    encryptTags(
                        privateTags =
                            privateTags.plus(
                                listAddresses.map { arrayOf("a", it.toTag()) },
                            ),
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
                create(
                    content = earlierVersion.content,
                    tags =
                        earlierVersion.tags.plus(
                            listAddresses.map { arrayOf("a", it.toTag()) },
                        ),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady,
                )
            }
        }

        fun addEvent(
            earlierVersion: CommunityListEvent,
            address: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = addTag(earlierVersion, "a", address.toTag(), isPrivate, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: CommunityListEvent,
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            earlierVersion.isTagged(key, tag, isPrivate, signer) { isTagged ->
                if (!isTagged) {
                    if (isPrivate) {
                        earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                            encryptTags(
                                privateTags = privateTags.plus(element = arrayOf(key, tag)),
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
                        create(
                            content = earlierVersion.content,
                            tags = earlierVersion.tags.plus(element = arrayOf(key, tag)),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady,
                        )
                    }
                }
            }
        }

        fun removeEvent(
            earlierVersion: CommunityListEvent,
            address: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = removeTag(earlierVersion, "a", address.toTag(), isPrivate, signer, createdAt, onReady)

        fun removeTag(
            earlierVersion: CommunityListEvent,
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            earlierVersion.isTagged(key, tag, isPrivate, signer) { isTagged ->
                if (isTagged) {
                    if (isPrivate) {
                        earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                            encryptTags(
                                privateTags =
                                    privateTags
                                        .filter { it.size > 1 && !(it[0] == key && it[1] == tag) }
                                        .toTypedArray(),
                                signer = signer,
                            ) { encryptedTags ->
                                create(
                                    content = encryptedTags,
                                    tags =
                                        earlierVersion.tags
                                            .filter { it.size > 1 && !(it[0] == key && it[1] == tag) }
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
                                    .filter { it.size > 1 && !(it[0] == key && it[1] == tag) }
                                    .toTypedArray(),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady,
                        )
                    }
                }
            }
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTagSerializer.toTagArray(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }
    }
}
