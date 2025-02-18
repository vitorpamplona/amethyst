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

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.geohash.isTaggedGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.countHashtags
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

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
    fun verifiedFollowKeySet(): Set<HexKey> = tags.mapNotNullTo(HashSet(), ContactTag::parseValidKey)

    /**
     * Returns a list of a-tags that are verified as correct.
     */
    fun verifiedFollowAddressSet(): Set<HexKey> = tags.mapNotNullTo(HashSet(), ATag::parseValidAddress)

    fun unverifiedFollowKeySet() = tags.mapNotNull(PTag::parseKey)

    fun unverifiedFollowTagSet() = tags.hashtags()

    fun countFollowTags() = tags.countHashtags()

    fun follows() = tags.mapNotNull(ContactTag::parseValid)

    fun followsTags() = hashtags()

    fun relays(): Map<String, ReadWrite>? = RelaySet.parse(content)

    companion object {
        const val KIND = 3
        const val ALT = "Follow List"

        fun blockListFor(pubKeyHex: HexKey): String = "3:$pubKeyHex:"

        fun createFromScratch(
            followUsers: List<ContactTag> = emptyList(),
            followTags: List<String> = emptyList(),
            followGeohashes: List<String> = emptyList(),
            followCommunities: List<ATag> = emptyList(),
            followEvents: List<String> = emptyList(),
            relayUse: Map<String, ReadWrite>? = emptyMap(),
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent? {
            val content = relayUse?.let { RelaySet.assemble(it) } ?: ""

            val tags =
                listOf(AltTag.assemble(ALT)) +
                    followUsers.map { it.toTagArray() } +
                    followTags.map { arrayOf("t", it) } +
                    followEvents.map { arrayOf("e", it) } +
                    followCommunities.map { it.toATagArray() } +
                    followGeohashes.map { arrayOf("g", it) }

            return signer.sign(createdAt, KIND, tags.toTypedArray(), content)
        }

        fun createFromScratch(
            followUsers: List<ContactTag>,
            followTags: List<String>,
            followGeohashes: List<String>,
            followCommunities: List<ATag>,
            followEvents: List<String>,
            relayUse: Map<String, ReadWrite>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ContactListEvent) -> Unit,
        ) {
            val content = relayUse?.let { RelaySet.assemble(it) } ?: ""

            val tags =
                followUsers.map { it.toTagArray() } +
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
            val content = relayUse?.let { RelaySet.assemble(it) } ?: ""

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
                    tags + AltTag.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }
    }
}
