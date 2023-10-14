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
    @Transient
    var decryptedContent: String? = null
    @Transient
    var publicAndPrivateUserCache: ImmutableSet<HexKey>? = null
    @Transient
    var publicAndPrivateWordCache: ImmutableSet<String>? = null

    fun filterTagList(key: String, privateTags: List<List<String>>?): ImmutableSet<String> {
        val privateUserList = privateTags?.let {
            it.filter { it.size > 1 && it[0] == key }.map { it[1] }.toSet()
        } ?: emptySet()
        val publicUserList = tags.filter { it.size > 1 && it[0] == key }.map { it[1] }.toSet()

        return (privateUserList + publicUserList).toImmutableSet()
    }

    fun publicAndPrivateWords(privateKey: ByteArray?): ImmutableSet<String> {
        publicAndPrivateWordCache?.let {
            return it
        }

        publicAndPrivateWordCache = filterTagList("word", privateTagsOrEmpty(privKey = privateKey))

        return publicAndPrivateWordCache ?: persistentSetOf()
    }

    fun publicAndPrivateUsers(privateKey: ByteArray?): ImmutableSet<HexKey> {
        publicAndPrivateUserCache?.let {
            return it
        }

        publicAndPrivateUserCache = filterTagList("p", privateTagsOrEmpty(privKey = privateKey))

        return publicAndPrivateUserCache ?: persistentSetOf()
    }

    fun publicAndPrivateWords(decryptedContent: String): ImmutableSet<HexKey> {
        publicAndPrivateWordCache?.let {
            return it
        }

        publicAndPrivateWordCache =  filterTagList("word", privateTagsOrEmpty(decryptedContent))

        return publicAndPrivateWordCache ?: persistentSetOf()
    }

    fun publicAndPrivateUsers(decryptedContent: String): ImmutableSet<HexKey> {
        publicAndPrivateUserCache?.let {
            return it
        }

        publicAndPrivateUserCache = filterTagList("p", privateTagsOrEmpty(decryptedContent))

        return publicAndPrivateUserCache ?: persistentSetOf()
    }

    fun isTagged(key: String, tag: String, isPrivate: Boolean, privateKey: ByteArray): Boolean {
        return if (isPrivate) {
            privateTagsOrEmpty(privKey = privateKey).any { it.size > 1 && it[0] == key && it[1] == tag }
        } else {
            isTagged(key, tag)
        }
    }

    fun isTagged(key: String, tag: String, isPrivate: Boolean, decryptedContent: String): Boolean {
        return if (isPrivate) {
            privateTagsOrEmpty(decryptedContent).any { it.size > 1 && it[0] == key && it[1] == tag }
        } else {
            isTagged(key, tag)
        }
    }

    fun isTaggedWord(word: String, isPrivate: Boolean, privateKey: ByteArray) = isTagged( "word", word, isPrivate, privateKey)

    fun isTaggedUser(idHex: String, isPrivate: Boolean, privateKey: ByteArray) = isTagged( "p", idHex, isPrivate, privateKey)

    fun isTaggedUser(idHex: String, isPrivate: Boolean, decryptedContent: String) = isTagged( "p", idHex, isPrivate, decryptedContent)

    fun isTaggedWord(idHex: String, isPrivate: Boolean, decryptedContent: String) = isTagged( "word", idHex, isPrivate, decryptedContent)

    companion object {
        const val kind = 30000
        const val blockList = "mute"

        fun blockListFor(pubKeyHex: HexKey): String {
            return "30000:$pubKeyHex:$blockList"
        }

        fun createListWithTag(name: String, key: String, tag: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return if (isPrivate) {
                create(
                    content = encryptTags(listOf(listOf(key, tag)), privateKey),
                    tags = listOf(listOf("d", name)),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = "",
                    tags = listOf(listOf("d", name), listOf(key, tag)),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            }
        }

        fun createListWithUser(name: String, pubKeyHex: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return createListWithTag(name, "p", pubKeyHex, isPrivate, privateKey, createdAt)
        }

        fun createListWithWord(name: String, word: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return createListWithTag(name, "word", word, isPrivate, privateKey, createdAt)
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

        fun createListWithWord(name: String, word: String, isPrivate: Boolean, pubKey: HexKey, encryptedContent: String, createdAt: Long = TimeUtils.now()): PeopleListEvent {
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
                    tags = listOf(listOf("d", name), listOf("word", word)),
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

        fun addWord(earlierVersion: PeopleListEvent, word: String, isPrivate: Boolean, pubKey: HexKey, encryptedContent: String, createdAt: Long = TimeUtils.now()): PeopleListEvent {
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
                    tags = earlierVersion.tags.plus(element = listOf("word", word)),
                    pubKey = pubKey,
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

        fun removeTag(earlierVersion: PeopleListEvent, tag: String, isPrivate: Boolean, pubKey: HexKey, encryptedContent: String, createdAt: Long = TimeUtils.now()): PeopleListEvent {
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
                    tags = earlierVersion.tags.filter { it.size > 1 && it[1] != tag },
                    pubKey = pubKey,
                    createdAt = createdAt
                )
            }
        }

        fun addWord(earlierVersion: PeopleListEvent, word: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return addTag(earlierVersion, "word", word, isPrivate, privateKey, createdAt)
        }

        fun addUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return addTag(earlierVersion, "p", pubKeyHex, isPrivate, privateKey, createdAt)
        }


        fun addTag(earlierVersion: PeopleListEvent, key: String, tag: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            if (earlierVersion.isTagged(key, tag, isPrivate, privateKey)) return earlierVersion

            return if (isPrivate) {
                create(
                    content = encryptTags(
                        privateTags = earlierVersion.privateTagsOrEmpty(privKey = privateKey).plus(element = listOf(key, tag)),
                        privateKey = privateKey
                    ),
                    tags = earlierVersion.tags,
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(element = listOf(key, tag)),
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            }
        }

        fun removeWord(earlierVersion: PeopleListEvent, word: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return removeTag(earlierVersion, "word", word, isPrivate, privateKey, createdAt)
        }

        fun removeUser(earlierVersion: PeopleListEvent, pubKeyHex: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            return removeTag(earlierVersion, "p", pubKeyHex, isPrivate, privateKey, createdAt)
        }

        fun removeTag(earlierVersion: PeopleListEvent, key: String, tag: String, isPrivate: Boolean, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): PeopleListEvent {
            if (!earlierVersion.isTagged(key, tag, isPrivate, privateKey)) return earlierVersion

            return if (isPrivate) {
                create(
                    content = encryptTags(
                        privateTags = earlierVersion.privateTagsOrEmpty(privKey = privateKey).filter { it.size > 1 && !(it[0] == key && it[1] == tag) },
                        privateKey = privateKey
                    ),
                    tags = earlierVersion.tags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) },
                    privateKey = privateKey,
                    createdAt = createdAt
                )
            } else {
                create(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.filter { it.size > 1 && !(it[0] == key && it[1] == tag) },
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
