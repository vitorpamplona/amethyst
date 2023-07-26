package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils

@Immutable
data class Contact(val pubKeyHex: String, val relayUri: String?)

@Stable
class ContactListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    // This function is only used by the user logged in
    // But it is used all the time.
    val verifiedFollowKeySet: Set<HexKey> by lazy {
        tags.filter { it.size > 1 && it[0] == "p" }.mapNotNull {
            try {
                decodePublicKey(it[1]).toHexKey()
            } catch (e: Exception) {
                Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
                null
            }
        }.toSet()
    }

    val verifiedFollowTagSet: Set<String> by lazy {
        unverifiedFollowTagSet().map { it.lowercase() }.toSet()
    }

    val verifiedFollowGeohashSet: Set<String> by lazy {
        unverifiedFollowGeohashSet().map { it.lowercase() }.toSet()
    }

    val verifiedFollowCommunitySet: Set<String> by lazy {
        unverifiedFollowAddressSet().toSet()
    }

    val verifiedFollowKeySetAndMe: Set<HexKey> by lazy {
        verifiedFollowKeySet + pubKey
    }

    fun unverifiedFollowKeySet() = tags.filter { it[0] == "p" }.mapNotNull { it.getOrNull(1) }
    fun unverifiedFollowTagSet() = tags.filter { it[0] == "t" }.mapNotNull { it.getOrNull(1) }
    fun unverifiedFollowGeohashSet() = tags.filter { it[0] == "g" }.mapNotNull { it.getOrNull(1) }

    fun unverifiedFollowAddressSet() = tags.filter { it[0] == "a" }.mapNotNull { it.getOrNull(1) }

    fun follows() = tags.filter { it[0] == "p" }.mapNotNull {
        try {
            Contact(decodePublicKey(it[1]).toHexKey(), it.getOrNull(2))
        } catch (e: Exception) {
            Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
            null
        }
    }

    fun followsTags() = tags.filter { it[0] == "t" }.mapNotNull {
        it.getOrNull(2)
    }

    fun relays(): Map<String, ReadWrite>? = try {
        if (content.isNotEmpty()) {
            gson.fromJson(content, object : TypeToken<Map<String, ReadWrite>>() {}.type) as Map<String, ReadWrite>
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w("ContactListEvent", "Can't parse content as relay lists: $content", e)
        null
    }

    companion object {
        const val kind = 3

        fun createFromScratch(
            followUsers: List<Contact>,
            followTags: List<String>,
            followGeohashes: List<String>,
            followCommunities: List<ATag>,
            followEvents: List<String>,
            relayUse: Map<String, ReadWrite>?,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): ContactListEvent {
            val content = if (relayUse != null) {
                gson.toJson(relayUse)
            } else {
                ""
            }

            val tags = followUsers.map {
                if (it.relayUri != null) {
                    listOf("p", it.pubKeyHex, it.relayUri)
                } else {
                    listOf("p", it.pubKeyHex)
                }
            } + followTags.map {
                listOf("t", it)
            } + followEvents.map {
                listOf("e", it)
            } + followCommunities.map {
                if (it.relay != null) {
                    listOf("a", it.toTag(), it.relay)
                } else {
                    listOf("a", it.toTag())
                }
            } + followGeohashes.map {
                listOf("g", it)
            }

            return create(
                content = content,
                tags = tags,
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun followUser(earlierVersion: ContactListEvent, pubKeyHex: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedUser(pubKeyHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("p", pubKeyHex)),
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun unfollowUser(earlierVersion: ContactListEvent, pubKeyHex: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedUser(pubKeyHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex },
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun followHashtag(earlierVersion: ContactListEvent, hashtag: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("t", hashtag)),
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun unfollowHashtag(earlierVersion: ContactListEvent, hashtag: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != hashtag },
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun followGeohash(earlierVersion: ContactListEvent, hashtag: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedGeoHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("g", hashtag)),
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun unfollowGeohash(earlierVersion: ContactListEvent, hashtag: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedGeoHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != hashtag },
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun followEvent(earlierVersion: ContactListEvent, idHex: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedEvent(idHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("e", idHex)),
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun unfollowEvent(earlierVersion: ContactListEvent, idHex: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedEvent(idHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != idHex },
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun followAddressableEvent(earlierVersion: ContactListEvent, aTag: ATag, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedAddressableNote(aTag.toTag())) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOfNotNull("a", aTag.toTag(), aTag.relay)),
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun unfollowAddressableEvent(earlierVersion: ContactListEvent, aTag: ATag, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedAddressableNote(aTag.toTag())) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != aTag.toTag() },
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun updateRelayList(earlierVersion: ContactListEvent, relayUse: Map<String, ReadWrite>?, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            val content = if (relayUse != null) {
                gson.toJson(relayUse)
            } else {
                ""
            }

            return create(
                content = content,
                tags = earlierVersion.tags,
                privateKey = privateKey,
                createdAt = createdAt
            )
        }

        fun create(content: String, tags: List<List<String>>, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ContactListEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ContactListEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }

    data class ReadWrite(val read: Boolean, val write: Boolean)
}
