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
package com.vitorpamplona.quartz.nip72ModCommunities.follow

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag.Companion.parseAsHint
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.GeneralListEvent
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayBuilder
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.coroutines.resume

@Immutable
class CommunityListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var publicAndPrivateAddressCache: Set<AddressHint>? = null

    fun publicCommunities() = tags.mapNotNull(ATag::parseAsHint)

    fun publicCommunityIds() = tags.mapNotNull(ATag::parseAddressId)

    fun publicAndCachedPrivateCommunityIds() = publicCommunityIds() + (publicAndPrivateAddressCache?.map { it.addressId } ?: emptyList())

    fun publicAndPrivateCommunities(
        signer: NostrSigner,
        onReady: (Set<AddressHint>) -> Unit,
    ) {
        publicAndPrivateAddressCache?.let { eventList ->
            onReady(eventList)
            return
        }

        mergeTagList(signer) {
            val set = it.mapNotNull(ATag::parseAsHint).toSet()
            publicAndPrivateAddressCache = set
            onReady(set)
        }
    }

    suspend fun publicAndPrivateCommunities(signer: NostrSigner): Set<AddressHint>? {
        publicAndPrivateAddressCache?.let { return it }

        return tryAndWait { continuation ->
            publicAndPrivateCommunities(signer) { privateTagList ->
                continuation.resume(privateTagList)
            }
        }
    }

    companion object {
        const val KIND = 10004
        const val ALT = "Community List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        private fun createCommunityBase(
            tags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.create(
                tags,
                isPrivate,
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        fun createCommunity(
            community: EventHintBundle<CommunityDefinitionEvent>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = createCommunityBase(
            tags = arrayOf(ATag.assemble(community.event.address(), community.relay)),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun createCommunity(
            community: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = createCommunityBase(
            tags = arrayOf(community.toATagArray()),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun createCommunities(
            communities: List<EventHintBundle<CommunityDefinitionEvent>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = createCommunityBase(
            tags = communities.map { ATag.assemble(it.event.address().toValue(), it.relay) }.toTypedArray(),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun removeCommunity(
            earlierVersion: CommunityListEvent,
            community: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.removeAll(
                earlierVersion,
                ATag.assemble(community, null),
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        private fun addCommunityBase(
            earlierVersion: CommunityListEvent,
            newTags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.addAll(
                earlierVersion,
                newTags,
                isPrivate,
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        fun addCommunity(
            earlierVersion: CommunityListEvent,
            community: ATag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = addCommunityBase(
            earlierVersion,
            arrayOf(community.toATagArray()),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        fun addCommunity(
            earlierVersion: CommunityListEvent,
            community: EventHintBundle<CommunityDefinitionEvent>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = addCommunityBase(
            earlierVersion,
            arrayOf(community.toATag().toATagArray()),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        fun addCommunity(
            earlierVersion: CommunityListEvent,
            community: AddressHint,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = addCommunityBase(
            earlierVersion,
            arrayOf(ATag.assemble(community.addressId, community.relay)),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        fun addCommunities(
            earlierVersion: CommunityListEvent,
            communities: List<EventHintBundle<CommunityDefinitionEvent>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) = addCommunityBase(
            earlierVersion,
            communities.map { it.toATag().toATagArray() }.toTypedArray(),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        private fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }

        fun create(
            list: List<AddressHint>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent? {
            val tags = list.map { ATag.assemble(it.addressId, it.relay) }.toTypedArray()
            return signer.sign(createdAt, KIND, tags, "")
        }
    }
}
