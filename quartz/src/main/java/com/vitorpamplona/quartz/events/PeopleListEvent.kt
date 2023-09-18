package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
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
    var decryptedContent: String? = null
    var publicAndPrivateUserCache: ImmutableSet<HexKey>? = null

    fun publicAndPrivateUsers(privateKey: ByteArray?): ImmutableSet<HexKey> {
        publicAndPrivateUserCache?.let {
            return it
        }

        val privateUserList = privateKey?.let {
            privateTagsOrEmpty(privKey = it).filter { it.size > 1 && it[0] == "p" }.map { it[1] }.toSet()
        } ?: emptySet()
        val publicUserList = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }.toSet()

        publicAndPrivateUserCache = (privateUserList + publicUserList).toImmutableSet()

        return publicAndPrivateUserCache ?: persistentSetOf()
    }

    fun publicAndPrivateUsers(decryptedContent: String): ImmutableSet<HexKey> {
        publicAndPrivateUserCache?.let {
            return it
        }

        val privateUserList = privateTagsOrEmpty(decryptedContent).filter { it.size > 1 && it[0] == "p" }.map { it[1] }.toSet()

        val publicUserList = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }.toSet()

        publicAndPrivateUserCache = (privateUserList + publicUserList).toImmutableSet()

        return publicAndPrivateUserCache ?: persistentSetOf()
    }

    fun isTaggedUser(idHex: String, isPrivate: Boolean, privateKey: ByteArray): Boolean {
        return if (isPrivate) {
            privateTagsOrEmpty(privKey = privateKey).any { it.size > 1 && it[0] == "p" && it[1] == idHex }
        } else {
            isTaggedUser(idHex)
        }
    }

    fun isTaggedUser(idHex: String, isPrivate: Boolean, content: String): Boolean {
        return if (isPrivate) {
            privateTagsOrEmpty(content).any { it.size > 1 && it[0] == "p" && it[1] == idHex }
        } else {
            isTaggedUser(idHex)
        }
    }

    companion object {
        const val kind = 30000
        const val blockList = "mute"

        fun createListWithUser(name: String, pubKeyHex: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return if (isPrivate) {
                create(
                    content = encryptTags(listOf(listOf("p", pubKeyHex)), privateKey),
                    tags = listOf(listOf("d", name)),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = "",
                    tags = listOf(listOf("d", name), listOf("p", pubKeyHex)),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            }
        }

        fun createListWithUser(name: String, pubKeyHex: String, isPrivate: Boolean, pubKey: HexKey, encryptedContent: String, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return if (isPrivate) {
                create(
                    content = encryptedContent,
                    tags = listOf(listOf("d", name)),
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = "",
                    tags = listOf(listOf("d", name), listOf("p", pubKeyHex)),
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            }
        }

        fun addUsers(earlierVersion: PeopleListEvent, listPubKeyHex: List<String>, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return if (isPrivate) {
                create(
                    content = encryptTags(
                        privateTags = earlierVersion.privateTagsOrEmpty(privKey = privateKey).plus(
                            listPubKeyHex.map {
                                listOf("p", it)
                            }
                        ),
                        privateKey = privateKey
                    ),
                    tags = earlierVersion.tags,
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(
                        listPubKeyHex.map {
                            listOf("p", it)
                        }
                    ),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            }
        }

        fun addUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, pubKey: HexKey, encryptedContent: String, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return if (isPrivate) {
                create(
                    content = encryptedContent,
                    tags = earlierVersion.tags,
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(element = listOf("p", pubKeyHex)),
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            }
        }

        fun addUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            if (earlierVersion.isTaggedUser(pubKeyHex, isPrivate, privateKey)) return earlierVersion

            return if (isPrivate) {
                create(
                    content = encryptTags(
                        privateTags = earlierVersion.privateTagsOrEmpty(privKey = privateKey).plus(element = listOf("p", pubKeyHex)),
                        privateKey = privateKey
                    ),
                    tags = earlierVersion.tags,
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(element = listOf("p", pubKeyHex)),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            }
        }

        fun removeUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, encryptedContent: String, pubKey: HexKey, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return if (isPrivate) {
                create(
                    content = encryptedContent,
                    tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex },
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex },
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            }
        }

        fun removeUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            if (!earlierVersion.isTaggedUser(pubKeyHex, isPrivate, privateKey)) return earlierVersion

            return if (isPrivate) {
                create(
                    content = encryptTags(
                        privateTags = earlierVersion.privateTagsOrEmpty(privKey = privateKey).filter { it.size > 1 && it[1] != pubKeyHex },
                        privateKey = privateKey
                    ),
                    tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex },
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex },
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            }
        }

        fun create(content: String, tags: List<List<String>>, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return PeopleListEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create(content: String, tags: List<List<String>>, pubKey: HexKey, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            val id = generateId(pubKey, createdAt, kind, tags, content)
            return PeopleListEvent(id.toHexKey(), pubKey, createdAt, tags, content, "")
        }

        fun create(unsignedEvent: PeopleListEvent, signature: String): PeopleListEvent {
            return PeopleListEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
