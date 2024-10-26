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
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import kotlinx.collections.immutable.ImmutableSet

@Immutable
class PeopleListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var publicAndPrivateUserCache: ImmutableSet<HexKey>? = null

    @Transient var publicAndPrivateWordCache: ImmutableSet<String>? = null

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (publicAndPrivateUserCache?.sumOf { pointerSizeInBytes + it.bytesUsedInMemory() } ?: 0) +
            pointerSizeInBytes + (publicAndPrivateWordCache?.sumOf { pointerSizeInBytes + it.bytesUsedInMemory() } ?: 0)

    fun publicAndPrivateWords(
        signer: NostrSigner,
        onReady: (ImmutableSet<String>) -> Unit,
    ) {
        publicAndPrivateWordCache?.let {
            onReady(it)
            return
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateWordCache = filterTagList("word", it)
            publicAndPrivateWordCache?.let { onReady(it) }
        }
    }

    fun publicAndPrivateUsers(
        signer: NostrSigner,
        onReady: (ImmutableSet<String>) -> Unit,
    ) {
        publicAndPrivateUserCache?.let {
            onReady(it)
            return
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateUserCache = filterTagList("p", it)
            publicAndPrivateUserCache?.let { onReady(it) }
        }
    }

    class FollowSet(
        val isPrivate: Boolean,
        val title: String,
        val description: String?,
        val profileList: Set<String>,
    )

    @Immutable
    class UsersAndWords(
        val users: Set<String> = setOf(),
        val words: Set<String> = setOf(),
    )

    fun publicAndPrivateUsersAndWords(
        signer: NostrSigner,
        onReady: (UsersAndWords) -> Unit,
    ) {
        publicAndPrivateUserCache?.let { userList ->
            publicAndPrivateWordCache?.let { wordList ->
                onReady(UsersAndWords(userList, wordList))
                return
            }
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateUserCache = filterTagList("p", it)
            publicAndPrivateWordCache = filterTagList("word", it)

            publicAndPrivateUserCache?.let { userList ->
                publicAndPrivateWordCache?.let { wordList ->
                    onReady(
                        UsersAndWords(userList, wordList),
                    )
                }
            }
        }
    }

    fun isTaggedWord(
        word: String,
        isPrivate: Boolean,
        signer: NostrSigner,
        onReady: (Boolean) -> Unit,
    ) = isTagged("word", word, isPrivate, signer, onReady)

    fun isTaggedUser(
        idHex: String,
        isPrivate: Boolean,
        signer: NostrSigner,
        onReady: (Boolean) -> Unit,
    ) = isTagged("p", idHex, isPrivate, signer, onReady)

    companion object {
        const val KIND = 30000
        const val BLOCK_LIST_D_TAG = "mute"
        const val ALT = "List of people"

        fun blockListFor(pubKeyHex: HexKey): String = "30000:$pubKeyHex:$BLOCK_LIST_D_TAG"

        fun createListWithTag(
            name: String,
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) {
            if (isPrivate) {
                encryptTags(arrayOf(arrayOf(key, tag)), signer) { encryptedTags ->
                    create(
                        content = encryptedTags,
                        tags = arrayOf(arrayOf("d", name)),
                        signer = signer,
                        createdAt = createdAt,
                        onReady = onReady,
                    )
                }
            } else {
                create(
                    content = "",
                    tags = arrayOf(arrayOf("d", name), arrayOf(key, tag)),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady,
                )
            }
        }

        fun createListWithUser(
            name: String,
            pubKeyHex: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) = createListWithTag(name, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)

        fun createListWithWord(
            name: String,
            word: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) = createListWithTag(name, "word", word, isPrivate, signer, createdAt, onReady)

        fun addUsers(
            earlierVersion: PeopleListEvent,
            listPubKeyHex: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) {
            if (isPrivate) {
                earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                    encryptTags(
                        privateTags =
                            privateTags.plus(
                                listPubKeyHex.map { arrayOf("p", it) },
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
                            listPubKeyHex.map { arrayOf("p", it) },
                        ),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady,
                )
            }
        }

        fun addWord(
            earlierVersion: PeopleListEvent,
            word: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) = addTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)

        fun addUser(
            earlierVersion: PeopleListEvent,
            pubKeyHex: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) = addTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: PeopleListEvent,
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
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

        fun removeWord(
            earlierVersion: PeopleListEvent,
            word: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) = removeTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)

        fun removeUser(
            earlierVersion: PeopleListEvent,
            pubKeyHex: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
        ) = removeTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)

        fun removeTag(
            earlierVersion: PeopleListEvent,
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit,
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
            onReady: (PeopleListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + arrayOf("alt", ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }
    }
}
