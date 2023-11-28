package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import kotlinx.collections.immutable.ImmutableSet
import java.util.UUID

@Immutable
class MuteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : GeneralListEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    var publicAndPrivateUserCache: ImmutableSet<HexKey>? = null
    @Transient
    var publicAndPrivateWordCache: ImmutableSet<String>? = null

    override fun dTag() = fixedDTag

    fun publicAndPrivateUsersAndWords(signer: NostrSigner, onReady: (PeopleListEvent.UsersAndWords) -> Unit) {
        publicAndPrivateUserCache?.let { userList ->
            publicAndPrivateWordCache?.let { wordList ->
                onReady(PeopleListEvent.UsersAndWords(userList, wordList))
                return
            }
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateUserCache = filterTagList("p", it)
            publicAndPrivateWordCache = filterTagList("word", it)

            publicAndPrivateUserCache?.let { userList ->
                publicAndPrivateWordCache?.let { wordList ->
                    onReady(
                        PeopleListEvent.UsersAndWords(userList, wordList)
                    )
                }
            }
        }
    }

    companion object {
        const val kind = 10000
        const val fixedDTag = ""

        fun blockListFor(pubKeyHex: HexKey): String {
            return "10000:$pubKeyHex:"
        }

        fun createListWithTag(key: String, tag: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            if (isPrivate) {
                encryptTags(arrayOf(arrayOf(key, tag)), signer) { encryptedTags ->
                    create(
                        content = encryptedTags,
                        tags = emptyArray(),
                        signer = signer,
                        createdAt = createdAt,
                        onReady = onReady
                    )
                }
            } else {
                create(
                    content = "",
                    tags = arrayOf(arrayOf(key, tag)),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady
                )
            }
        }

        fun createListWithUser(pubKeyHex: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            return createListWithTag("p", pubKeyHex, isPrivate, signer, createdAt, onReady)
        }

        fun createListWithWord(word: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            return createListWithTag("word", word, isPrivate, signer, createdAt, onReady)
        }

        fun addUsers(earlierVersion: MuteListEvent, listPubKeyHex: List<String>, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            if (isPrivate) {
                earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                    encryptTags(
                        privateTags = privateTags.plus(
                            listPubKeyHex.map {
                                arrayOf("p", it)
                            }
                        ),
                        signer = signer
                    ) { encryptedTags ->
                        create(
                            content = encryptedTags,
                            tags = earlierVersion.tags,
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady
                        )
                    }
                }
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(
                        listPubKeyHex.map {
                            arrayOf("p", it)
                        }
                    ),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady
                )
            }
        }

        fun addWord(earlierVersion: MuteListEvent, word: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            return addTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)
        }

        fun addUser(earlierVersion: MuteListEvent, pubKeyHex: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            return addTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)
        }

        fun addTag(earlierVersion: MuteListEvent, key: String, tag: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            earlierVersion.isTagged(key, tag, isPrivate, signer) { isTagged ->
                if (!isTagged) {
                    if (isPrivate) {
                        earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                            encryptTags(
                                privateTags = privateTags.plus(element = arrayOf(key, tag)),
                                signer = signer
                            ) { encryptedTags ->
                                create(
                                    content = encryptedTags,
                                    tags = earlierVersion.tags,
                                    signer = signer,
                                    createdAt = createdAt,
                                    onReady = onReady
                                )
                            }
                        }
                    } else {
                        create(
                            content = earlierVersion.content,
                            tags = earlierVersion.tags.plus(element = arrayOf(key, tag)),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady
                        )
                    }
                }
            }
        }

        fun removeWord(earlierVersion: MuteListEvent, word: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            return removeTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)
        }

        fun removeUser(earlierVersion: MuteListEvent, pubKeyHex: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            return removeTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)
        }

        fun removeTag(earlierVersion: MuteListEvent, key: String, tag: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (MuteListEvent) -> Unit) {
            earlierVersion.isTagged(key, tag, isPrivate, signer) { isTagged ->
                if (isTagged) {
                    if (isPrivate) {
                        earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                            encryptTags(
                                privateTags = privateTags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) }.toTypedArray(),
                                signer = signer
                            ) { encryptedTags ->
                                create(
                                    content = encryptedTags,
                                    tags = earlierVersion.tags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) }.toTypedArray(),
                                    signer = signer,
                                    createdAt = createdAt,
                                    onReady = onReady
                                )
                            }
                        }
                    } else {
                        create(
                            content = earlierVersion.content,
                            tags = earlierVersion.tags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) }.toTypedArray(),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady
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
            onReady: (MuteListEvent) -> Unit
        ) {
            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
