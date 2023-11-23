package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class PeopleListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : GeneralListEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    var publicAndPrivateUserCache: ImmutableSet<HexKey>? = null
    @Transient
    var publicAndPrivateWordCache: ImmutableSet<String>? = null

    fun publicAndPrivateWords(signer: NostrSigner, onReady: (ImmutableSet<String>) -> Unit) {
        publicAndPrivateWordCache?.let {
            onReady(it)
            return
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateWordCache = filterTagList("word", it)
            publicAndPrivateWordCache?.let {
                onReady(it)
            }
        }
    }

    fun publicAndPrivateUsers(signer: NostrSigner, onReady: (ImmutableSet<String>) -> Unit) {
        publicAndPrivateUserCache?.let {
            onReady(it)
            return
        }

        privateTagsOrEmpty(signer) {
            publicAndPrivateUserCache = filterTagList("p", it)
            publicAndPrivateUserCache?.let {
                onReady(it)
            }
        }
    }

    @Immutable
    data class UsersAndWords(
        val users: ImmutableSet<String> = persistentSetOf(),
        val words: ImmutableSet<String> = persistentSetOf()
    )

    fun publicAndPrivateUsersAndWords(signer: NostrSigner, onReady: (UsersAndWords) -> Unit) {
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
                        UsersAndWords(userList, wordList)
                    )
                }
            }
        }
    }

    fun isTaggedWord(word: String, isPrivate: Boolean, signer: NostrSigner, onReady: (Boolean) -> Unit) = isTagged( "word", word, isPrivate, signer, onReady)

    fun isTaggedUser(idHex: String, isPrivate: Boolean, signer: NostrSigner, onReady: (Boolean) -> Unit) = isTagged( "p", idHex, isPrivate, signer, onReady)

    companion object {
        const val kind = 30000
        const val blockList = "mute"

        fun blockListFor(pubKeyHex: HexKey): String {
            return "30000:$pubKeyHex:$blockList"
        }

        fun createListWithTag(name: String, key: String, tag: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            if (isPrivate) {
                encryptTags(listOf(listOf(key, tag)), signer) { encryptedTags ->
                    create(
                        content = encryptedTags,
                        tags = listOf(listOf("d", name)),
                        signer = signer,
                        createdAt = createdAt,
                        onReady = onReady
                    )
                }
            } else {
                create(
                    content = "",
                    tags = listOf(listOf("d", name), listOf(key, tag)),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady
                )
            }
        }

        fun createListWithUser(name: String, pubKeyHex: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            return createListWithTag(name, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)
        }

        fun createListWithWord(name: String, word: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            return createListWithTag(name, "word", word, isPrivate, signer, createdAt, onReady)
        }

        fun addUsers(earlierVersion: PeopleListEvent, listPubKeyHex: List<String>, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            if (isPrivate) {
                earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                    encryptTags(
                        privateTags = privateTags.plus(
                            listPubKeyHex.map {
                                listOf("p", it)
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
                            listOf("p", it)
                        }
                    ),
                    signer = signer,
                    createdAt = createdAt,
                    onReady = onReady
                )
            }
        }

        fun addWord(earlierVersion: PeopleListEvent, word: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            return addTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)
        }

        fun addUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            return addTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)
        }

        fun addTag(earlierVersion: PeopleListEvent, key: String, tag: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            earlierVersion.isTagged(key, tag, isPrivate, signer) { isTagged ->
                if (!isTagged) {
                    if (isPrivate) {
                        earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                            encryptTags(
                                privateTags = privateTags.plus(element = listOf(key, tag)),
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
                            tags = earlierVersion.tags.plus(element = listOf(key, tag)),
                            signer = signer,
                            createdAt = createdAt,
                            onReady = onReady
                        )
                    }
                }
            }
        }

        fun removeWord(earlierVersion: PeopleListEvent, word: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            return removeTag(earlierVersion, "word", word, isPrivate, signer, createdAt, onReady)
        }

        fun removeUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            return removeTag(earlierVersion, "p", pubKeyHex, isPrivate, signer, createdAt, onReady)
        }

        fun removeTag(earlierVersion: PeopleListEvent, key: String, tag: String, isPrivate: Boolean, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (PeopleListEvent) -> Unit) {
            earlierVersion.isTagged(key, tag, isPrivate, signer) { isTagged ->
                if (isTagged) {
                    if (isPrivate) {
                        earlierVersion.privateTagsOrEmpty(signer) { privateTags ->
                            encryptTags(
                                privateTags = privateTags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) },
                                signer = signer
                            ) { encryptedTags ->
                                create(
                                    content = encryptedTags,
                                    tags = earlierVersion.tags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) },
                                    signer = signer,
                                    createdAt = createdAt,
                                    onReady = onReady
                                )
                            }
                        }
                    } else {
                        create(
                            content = earlierVersion.content,
                            tags = earlierVersion.tags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) },
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
            tags: List<List<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PeopleListEvent) -> Unit
        ) {
            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
