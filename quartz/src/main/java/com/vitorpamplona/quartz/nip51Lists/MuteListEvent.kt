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
package com.vitorpamplona.quartz.nip51Lists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class MuteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun dTag() = FIXED_D_TAG

    fun publicAndPrivateUsersAndWords(
        signer: NostrSigner,
        onReady: (PeopleListEvent.UsersAndWords) -> Unit,
    ) {
        privateTagsOrEmpty(signer) {
            onReady(
                PeopleListEvent.UsersAndWords(filterTagList("p", it), filterTagList("word", it)),
            )
        }
    }

    companion object {
        const val KIND = 10000
        const val FIXED_D_TAG = ""
        const val ALT = "Mute List"

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        fun blockListFor(pubKeyHex: HexKey): String = "10000:$pubKeyHex:"

        fun createListWithTag(
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
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

        fun createListWithUser(
            pubKeyHex: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) = createListWithTag("p", pubKeyHex, isPrivate, signer, createdAt, onReady)

        fun createListWithWord(
            word: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) = createListWithTag("word", word, isPrivate, signer, createdAt, onReady)

        fun addUsers(
            earlierVersion: MuteListEvent,
            listPubKeyHex: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
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
            earlierVersion: MuteListEvent,
            word: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) = addTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)

        fun addUser(
            earlierVersion: MuteListEvent,
            pubKeyHex: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) = addTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)

        fun addTag(
            earlierVersion: MuteListEvent,
            key: String,
            tag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
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
            earlierVersion: MuteListEvent,
            word: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) = removeTag(earlierVersion, "word", word, signer, createdAt, onReady)

        fun removeUser(
            earlierVersion: MuteListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) = removeTag(earlierVersion, "p", pubKeyHex, signer, createdAt, onReady)

        fun removeTag(
            earlierVersion: MuteListEvent,
            key: String,
            tag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
        ) {
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
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit,
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
}
