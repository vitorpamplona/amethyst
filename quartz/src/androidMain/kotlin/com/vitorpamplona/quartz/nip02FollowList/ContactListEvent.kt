/**
 * Copyright (c) 2025 Vitor Pamplona
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
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
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
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider {
    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints() = tags.mapNotNull(ContactTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(ContactTag::parseKey)

    /**
     * Returns a list of p-tags that are verified as hex keys.
     */
    fun verifiedFollowKeySet(): Set<HexKey> = tags.mapNotNullTo(HashSet(), ContactTag::parseValidKey)

    /**
     * Returns a list of a-tags that are verified as correct.
     */
    @Deprecated("Use CommunityListEvent instead.")
    fun verifiedFollowAddressSet(): Set<HexKey> = tags.mapNotNullTo(HashSet(), ATag::parseValidAddress)

    fun unverifiedFollowKeySet() = tags.mapNotNull(ContactTag::parseKey)

    @Deprecated("Use HashtagListEvent instead.")
    fun unverifiedFollowTagSet() = tags.hashtags()

    fun follows() = tags.mapNotNull(ContactTag::parseValid)

    fun relays(): Map<NormalizedRelayUrl, ReadWrite>? {
        val regular = RelaySet.parse(content)

        val normalized = mutableMapOf<NormalizedRelayUrl, ReadWrite>()

        regular?.forEach {
            val key = RelayUrlNormalizer.normalizeOrNull(it.key)
            if (key != null) {
                normalized.put(key, it.value)
            }
        }

        return normalized
    }

    companion object {
        const val KIND = 3
        const val ALT = "Follow List"

        fun blockListFor(pubKeyHex: HexKey): String = "3:$pubKeyHex:"

        suspend fun createFromScratch(
            followUsers: List<ContactTag>,
            relayUse: Map<String, ReadWrite>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent {
            val content = relayUse?.let { RelaySet.assemble(it) } ?: ""

            val tags = followUsers.map { it.toTagArray() }
            return create(
                content = content,
                tags = tags.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun followUser(
            earlierVersion: ContactListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent {
            if (earlierVersion.isTaggedUser(pubKeyHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(element = arrayOf("p", pubKeyHex)),
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun unfollowUser(
            earlierVersion: ContactListEvent,
            pubKeyHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent {
            if (!earlierVersion.isTaggedUser(pubKeyHex)) return earlierVersion

            return create(
                content = earlierVersion.content,
                tags = earlierVersion.tags.filter { it.size > 1 && it[1] != pubKeyHex }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun updateRelayList(
            earlierVersion: ContactListEvent,
            relayUse: Map<String, ReadWrite>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent {
            val content = relayUse?.let { RelaySet.assemble(it) } ?: ""

            return create(
                content = content,
                tags = earlierVersion.tags,
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        fun createFromScratch(
            followUsers: List<ContactTag> = emptyList(),
            relayUse: Map<String, ReadWrite>? = emptyMap(),
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ContactListEvent {
            val content = relayUse?.let { RelaySet.assemble(it) } ?: ""

            val tags =
                listOf(AltTag.assemble(ALT)) +
                    followUsers.map { it.toTagArray() }

            return signer.sign(createdAt, KIND, tags.toTypedArray(), content)
        }
    }
}
