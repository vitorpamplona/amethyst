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

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.decodePublicKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable data class Contact(val pubKeyHex: String, val relayUri: String?)

@Stable
class ContactListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    // This function is only used by the user logged in
    // But it is used all the time.

    @delegate:Transient
    val verifiedFollowKeySet: Set<HexKey> by lazy {
        tags.mapNotNullTo(HashSet()) {
            try {
                if (it.size > 1 && it[0] == "p") {
                    decodePublicKey(it[1]).toHexKey()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
                null
            }
        }
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
    val verifiedFollowCommunitySet: Set<String> by lazy { unverifiedFollowAddressSet().toSet() }

    @delegate:Transient
    val verifiedFollowKeySetAndMe: Set<HexKey> by lazy { verifiedFollowKeySet + pubKey }

    fun unverifiedFollowKeySet() = tags.filter { it.size > 1 && it[0] == "p" }.mapNotNull { it.getOrNull(1) }

    fun unverifiedFollowTagSet() = tags.filter { it.size > 1 && it[0] == "t" }.mapNotNull { it.getOrNull(1) }

    fun unverifiedFollowGeohashSet() = tags.filter { it.size > 1 && it[0] == "g" }.mapNotNull { it.getOrNull(1) }

    fun unverifiedFollowAddressSet() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull { it.getOrNull(1) }

    fun follows() =
        tags.mapNotNull {
            try {
                if (it.size > 1 && it[0] == "p") {
                    Contact(decodePublicKey(it[1]).toHexKey(), it.getOrNull(2))
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
                null
            }
        }

    fun followsTags() = tags.filter { it.size > 1 && it[0] == "t" }.mapNotNull { it.getOrNull(1) }

    fun relays(): Map<String, ReadWrite>? =
        try {
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
        const val KIND = 3
        const val ALT = "Follow List"

        fun createFromScratch(
            followUsers: List<Contact>,
            followTags: List<String>,
            followGeohashes: List<String>,
            followCommunities: List<ATag>,
            followEvents: List<String>,
            relayUse: Map<String, ReadWrite>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            val content =
                if (relayUse != null) {
                    mapper.writeValueAsString(relayUse)
                } else {
                    ""
                }

            val tags =
                followUsers.map {
                    if (it.relayUri != null) {
                        arrayOf("p", it.pubKeyHex, it.relayUri)
                    } else {
                        arrayOf("p", it.pubKeyHex)
                    }
                } +
                    followTags.map { arrayOf("t", it) } +
                    followEvents.map { arrayOf("e", it) } +
                    followCommunities.map {
                        if (it.relay != null) {
                            arrayOf("a", it.toTag(), it.relay)
                        } else {
                            arrayOf("a", it.toTag())
                        }
                    } +
                    followGeohashes.map { arrayOf("g", it) }

            return create(
                content = content,
                tags = tags.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun followUser(
            earlierVersion: ContactListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (earlierVersion.isTaggedUser(pubKeyHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("p", pubKeyHex)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun unfollowUser(
            earlierVersion: ContactListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (!earlierVersion.isTaggedUser(pubKeyHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun followHashtag(
            earlierVersion: ContactListEvent,
            hashtag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (earlierVersion.isTaggedHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("t", hashtag)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun unfollowHashtag(
            earlierVersion: ContactListEvent,
            hashtag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (!earlierVersion.isTaggedHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags.filter { it.size > 1 && !it[1].equals(hashtag, true) }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun followGeohash(
            earlierVersion: ContactListEvent,
            hashtag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (earlierVersion.isTaggedGeoHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("g", hashtag)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun unfollowGeohash(
            earlierVersion: ContactListEvent,
            hashtag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (!earlierVersion.isTaggedGeoHash(hashtag)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != hashtag }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun followEvent(
            earlierVersion: ContactListEvent,
            idHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (earlierVersion.isTaggedEvent(idHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("e", idHex)),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun unfollowEvent(
            earlierVersion: ContactListEvent,
            idHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (!earlierVersion.isTaggedEvent(idHex)) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != idHex }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun followAddressableEvent(
            earlierVersion: ContactListEvent,
            aTag: ATag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (earlierVersion.isTaggedAddressableNote(aTag.toTag())) return

            return create(
                content = earlierVersion.content,
                tags =
                    earlierVersion.tags.plus(
                        element = listOfNotNull("a", aTag.toTag(), aTag.relay).toTypedArray(),
                    ),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun unfollowAddressableEvent(
            earlierVersion: ContactListEvent,
            aTag: ATag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            if (!earlierVersion.isTaggedAddressableNote(aTag.toTag())) return

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != aTag.toTag() }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun updateRelayList(
            earlierVersion: ContactListEvent,
            relayUse: Map<String, ReadWrite>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            val content =
                if (relayUse != null) {
                    mapper.writeValueAsString(relayUse)
                } else {
                    ""
                }

            return create(
                content = content,
                tags = earlierVersion.tags,
                signer = signer,
                createdAt = createdAt,
                onReady = onReady,
            )
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
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

    data class ReadWrite(val read: Boolean, val write: Boolean)
}

@Stable
class UserMetadata {
    var name: String? = null
    var username: String? = null

    @JsonProperty("display_name")
    var displayName: String? = null
    var picture: String? = null
    var banner: String? = null
    var website: String? = null
    var about: String? = null
    var bot: Boolean? = null

    var nip05: String? = null
    var nip05Verified: Boolean = false
    var nip05LastVerificationTime: Long? = 0

    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    var twitter: String? = null

    @Transient
    var tags: ImmutableListOfLists<String>? = null

    fun anyName(): String? {
        return displayName ?: name ?: username
    }

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(name, username, displayName, nip05, lud06, lud16).any {
            it.contains(prefix, true)
        }
    }

    fun lnAddress(): String? {
        return lud16 ?: lud06
    }

    fun bestName(): String? {
        return displayName ?: name ?: username
    }

    fun nip05(): String? {
        return nip05
    }

    fun profilePicture(): String? {
        return picture
    }

    fun cleanBlankNames() {
        if (picture?.isNotEmpty() == true) picture = picture?.trim()
        if (nip05?.isNotEmpty() == true) nip05 = nip05?.trim()
        if (displayName?.isNotEmpty() == true) displayName = displayName?.trim()
        if (name?.isNotEmpty() == true) name = name?.trim()
        if (username?.isNotEmpty() == true) username = username?.trim()
        if (lud06?.isNotEmpty() == true) lud06 = lud06?.trim()
        if (lud16?.isNotEmpty() == true) lud16 = lud16?.trim()

        if (banner?.isNotEmpty() == true) banner = banner?.trim()
        if (website?.isNotEmpty() == true) website = website?.trim()
        if (domain?.isNotEmpty() == true) domain = domain?.trim()

        if (picture?.isBlank() == true) picture = null
        if (nip05?.isBlank() == true) nip05 = null
        if (displayName?.isBlank() == true) displayName = null
        if (name?.isBlank() == true) name = null
        if (username?.isBlank() == true) username = null
        if (lud06?.isBlank() == true) lud06 = null
        if (lud16?.isBlank() == true) lud16 = null

        if (banner?.isBlank() == true) banner = null
        if (website?.isBlank() == true) website = null
        if (domain?.isBlank() == true) domain = null
    }
}

@Stable class ImmutableListOfLists<T>(val lists: Array<Array<T>>)

val EmptyTagList = ImmutableListOfLists<String>(emptyArray())

fun Array<Array<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> {
    return ImmutableListOfLists(this)
}
