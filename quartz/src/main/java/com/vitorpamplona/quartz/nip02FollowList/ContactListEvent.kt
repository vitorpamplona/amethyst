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
package com.vitorpamplona.quartz.nip02FollowList

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.addressables.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.events.isTaggedEvent
import com.vitorpamplona.quartz.nip01Core.geohash.isTaggedGeoHash
import com.vitorpamplona.quartz.nip01Core.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.hashtags.isTaggedHash
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.people.isTaggedUser
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32Entities.decodePublicKey
import com.vitorpamplona.quartz.nip19Bech32Entities.parse
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable data class Contact(
    val pubKeyHex: String,
    val relayUri: String?,
)

@Stable
class ContactListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Returns a list of p-tags that are verified as hex keys.
     */
    fun verifiedFollowKeySet(): Set<HexKey> =
        tags.mapNotNullTo(mutableSetOf()) {
            if (it.size > 1 && it[0] == "p") {
                try {
                    decodePublicKey(it[1]).toHexKey()
                } catch (e: Exception) {
                    Log.w("ContactListEvent", "Can't parse p-tag $it in the contact list of $pubKey with id $id", e)
                    null
                }
            } else {
                null
            }
        }

    /**
     * Returns a list of a-tags that are verified as correct.
     */
    fun verifiedFollowAddressSet(): Set<HexKey> =
        tags
            .mapNotNullTo(mutableSetOf()) {
                if (it.size > 1 && it[0] == "a") {
                    ATag.parse(it[1], null)?.toTag()
                } else {
                    null
                }
            }

    fun unverifiedFollowKeySet() = tags.filter { it.size > 1 && it[0] == "p" }.mapNotNull { it.getOrNull(1) }

    fun unverifiedFollowTagSet() = tags.filter { it.size > 1 && it[0] == "t" }.mapNotNull { it.getOrNull(1) }

    fun countFollowTags() = tags.count { it.size > 1 && it[0] == "t" }

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

    fun followsTags() = hashtags()

    fun relays(): Map<String, ReadWrite>? =
        try {
            if (content.isNotEmpty()) {
                EventMapper.mapper.readValue<Map<String, ReadWrite>>(content)
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

        fun blockListFor(pubKeyHex: HexKey): String = "3:$pubKeyHex:"

        fun createFromScratch(
            followUsers: List<Contact> = emptyList(),
            followTags: List<String> = emptyList(),
            followGeohashes: List<String> = emptyList(),
            followCommunities: List<ATag> = emptyList(),
            followEvents: List<String> = emptyList(),
            relayUse: Map<String, ReadWrite>? = emptyMap(),
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent? {
            val content =
                if (relayUse != null) {
                    EventMapper.mapper.writeValueAsString(relayUse)
                } else {
                    ""
                }

            val tags =
                listOf(arrayOf("alt", ALT)) +
                    followUsers.map {
                        listOfNotNull("p", it.pubKeyHex, it.relayUri).toTypedArray()
                    } +
                    followTags.map { arrayOf("t", it) } +
                    followEvents.map { arrayOf("e", it) } +
                    followCommunities.map { it.toATagArray() } +
                    followGeohashes.map { arrayOf("g", it) }

            return signer.sign(createdAt, KIND, tags.toTypedArray(), content)
        }

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
                    EventMapper.mapper.writeValueAsString(relayUse)
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
                    followCommunities.map { it.toATagArray() } +
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
                    EventMapper.mapper.writeValueAsString(relayUse)
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

    data class ReadWrite(
        val read: Boolean,
        val write: Boolean,
    )
}

@Stable class ImmutableListOfLists<T>(
    val lists: Array<Array<T>>,
)

val EmptyTagList = ImmutableListOfLists<String>(emptyArray())

fun Array<Array<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> = ImmutableListOfLists(this)
