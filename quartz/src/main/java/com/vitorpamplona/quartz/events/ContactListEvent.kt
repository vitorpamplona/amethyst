package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.decodePublicKey

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

    @delegate:Transient
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

    @delegate:Transient
    val verifiedFollowTagSet: Set<String> by lazy {
        unverifiedFollowTagSet().map { it.lowercase() }.toSet()
    }

    @delegate:Transient
    val verifiedFollowGeohashSet: Set<String> by lazy {
        unverifiedFollowGeohashSet().map { it.lowercase() }.toSet()
    }

    @delegate:Transient
    val verifiedFollowCommunitySet: Set<String> by lazy {
        unverifiedFollowAddressSet().toSet()
    }

    @delegate:Transient
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
            mapper.readValue<Map<String, ReadWrite>>(content)
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
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): ContactListEvent {
            val content = if (relayUse != null) {
                mapper.writeValueAsString(relayUse)
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
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun followUser(earlierVersion: ContactListEvent, pubKeyHex: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedUser(pubKeyHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("p", pubKeyHex)),
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun unfollowUser(earlierVersion: ContactListEvent, pubKeyHex: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedUser(pubKeyHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex },
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun followHashtag(earlierVersion: ContactListEvent, hashtag: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("t", hashtag)),
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun unfollowHashtag(earlierVersion: ContactListEvent, hashtag: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && !it[1].equals(hashtag, true) },
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun followGeohash(earlierVersion: ContactListEvent, hashtag: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedGeoHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("g", hashtag)),
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun unfollowGeohash(earlierVersion: ContactListEvent, hashtag: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedGeoHash(hashtag)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != hashtag },
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun followEvent(earlierVersion: ContactListEvent, idHex: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedEvent(idHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOf("e", idHex)),
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun unfollowEvent(earlierVersion: ContactListEvent, idHex: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedEvent(idHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != idHex },
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun followAddressableEvent(earlierVersion: ContactListEvent, aTag: ATag, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (earlierVersion.isTaggedAddressableNote(aTag.toTag())) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOfNotNull("a", aTag.toTag(), aTag.relay)),
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun unfollowAddressableEvent(earlierVersion: ContactListEvent, aTag: ATag, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            if (!earlierVersion.isTaggedAddressableNote(aTag.toTag())) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != aTag.toTag() },
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun updateRelayList(earlierVersion: ContactListEvent, relayUse: Map<String, ReadWrite>?, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ContactListEvent {
            val content = if (relayUse != null) {
                mapper.writeValueAsString(relayUse)
            } else {
                ""
            }

            return create(
                content = content,
                tags = earlierVersion.tags,
                keyPair = keyPair,
                createdAt = createdAt
            )
        }

        fun create(
            unsignedEvent: Event,
            signature: String
        ): ContactListEvent {
            return ContactListEvent(
                unsignedEvent.id,
                unsignedEvent.pubKey,
                unsignedEvent.createdAt,
                unsignedEvent.tags,
                unsignedEvent.content,
                signature
            )
        }

        fun create(
            content: String,
            tags: List<List<String>>,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): ContactListEvent {
            val pubKey = keyPair.pubKey.toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return ContactListEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }
    }

    data class ReadWrite(val read: Boolean, val write: Boolean)
}

@Stable
class UserMetadata {
    var name: String? = null
    var username: String? = null
    var display_name: String? = null
    var displayName: String? = null
    var picture: String? = null
    var banner: String? = null
    var website: String? = null
    var about: String? = null

    var nip05: String? = null
    var nip05Verified: Boolean = false
    var nip05LastVerificationTime: Long? = 0

    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    var twitter: String? = null

    var updatedMetadataAt: Long = 0
    var latestMetadata: MetadataEvent? = null
    var tags: ImmutableListOfLists<String>? = null

    fun anyName(): String? {
        return display_name ?: displayName ?: name ?: username
    }

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(name, username, display_name, displayName, nip05, lud06, lud16)
            .any { it.contains(prefix, true) }
    }

    fun lnAddress(): String? {
        return (lud16?.trim() ?: lud06?.trim())?.ifBlank { null }
    }

    fun bestUsername(): String? {
        return name?.ifBlank { null } ?: username?.ifBlank { null }
    }

    fun bestDisplayName(): String? {
        return displayName?.ifBlank { null } ?: display_name?.ifBlank { null }
    }

    fun nip05(): String? {
        return nip05?.ifBlank { null }
    }

    fun profilePicture(): String? {
        if (picture.isNullOrBlank()) picture = null
        return picture
    }
}

@Stable
data class ImmutableListOfLists<T>(val lists: List<List<T>> = emptyList())

fun List<List<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> {
    return ImmutableListOfLists(this)
}