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
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
data class Contact(val pubKeyHex: String, val relayUri: String?)

@Stable
class ContactListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit
        ) {
            val content = if (relayUse != null) {
                mapper.writeValueAsString(relayUse)
            } else {
                ""
            }

            val tags = followUsers.map {
                if (it.relayUri != null) {
                    arrayOf("p", it.pubKeyHex, it.relayUri)
                } else {
                    arrayOf("p", it.pubKeyHex)
                }
            } + followTags.map {
                arrayOf("t", it)
            } + followEvents.map {
                arrayOf("e", it)
            } + followCommunities.map {
                if (it.relay != null) {
                    arrayOf("a", it.toTag(), it.relay)
                } else {
                    arrayOf("a", it.toTag())
                }
            } + followGeohashes.map {
                arrayOf("g", it)
            }

            return create(
                content = content,
                tags = tags.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun followUser(earlierVersion: ContactListEvent, pubKeyHex: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (earlierVersion.isTaggedUser(pubKeyHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("p", pubKeyHex)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun unfollowUser(earlierVersion: ContactListEvent, pubKeyHex: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (!earlierVersion.isTaggedUser(pubKeyHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun followHashtag(earlierVersion: ContactListEvent, hashtag: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (earlierVersion.isTaggedHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("t", hashtag)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun unfollowHashtag(earlierVersion: ContactListEvent, hashtag: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (!earlierVersion.isTaggedHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && !it[1].equals(hashtag, true) }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun followGeohash(earlierVersion: ContactListEvent, hashtag: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (earlierVersion.isTaggedGeoHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("g", hashtag)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun unfollowGeohash(earlierVersion: ContactListEvent, hashtag: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (!earlierVersion.isTaggedGeoHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != hashtag }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun followEvent(earlierVersion: ContactListEvent, idHex: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (earlierVersion.isTaggedEvent(idHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("e", idHex)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun unfollowEvent(earlierVersion: ContactListEvent, idHex: String, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (!earlierVersion.isTaggedEvent(idHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != idHex }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun followAddressableEvent(earlierVersion: ContactListEvent, aTag: ATag, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (earlierVersion.isTaggedAddressableNote(aTag.toTag())) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = listOfNotNull("a", aTag.toTag(), aTag.relay).toTypedArray()),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun unfollowAddressableEvent(earlierVersion: ContactListEvent, aTag: ATag, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            if (!earlierVersion.isTaggedAddressableNote(aTag.toTag())) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != aTag.toTag() }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun updateRelayList(earlierVersion: ContactListEvent, relayUse: Map<String, ReadWrite>?, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ContactListEvent) -> Unit) {
            val content = if (relayUse != null) {
                mapper.writeValueAsString(relayUse)
            } else {
                ""
            }

            return create(
                content = content,
                tags = earlierVersion.tags,
                signer = signer,
                createdAt = createdAt,
                onReady = onReady
            )
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit
        ) {
            signer.sign(createdAt, kind, tags, content, onReady)
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
data class ImmutableListOfLists<T>(val lists: Array<Array<T>>)

val EmptyTagList = ImmutableListOfLists<String>(emptyArray())

fun Array<Array<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> {
    return ImmutableListOfLists(this)
}