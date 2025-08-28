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
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.removeAny
import com.vitorpamplona.quartz.nip72ModCommunities.follow.tags.CommunityTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.plus

@Immutable
class CommunityListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints() = tags.mapNotNull(CommunityTag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(CommunityTag::parseAddressId)

    fun publicCommunities() = tags.communities()

    fun publicCommunityIds() = tags.communityIds()

    companion object {
        const val KIND = 10004
        const val ALT = "Community List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun create(
            communities: List<CommunityTag>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent =
            if (isPrivate) {
                create(
                    publicCommunities = emptyList(),
                    privateCommunities = communities,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicCommunities = communities,
                    privateCommunities = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun create(
            community: CommunityTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent =
            if (isPrivate) {
                create(
                    publicCommunities = emptyList(),
                    privateCommunities = listOf(community),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicCommunities = listOf(community),
                    privateCommunities = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: CommunityListEvent,
            communities: List<CommunityTag>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.removeAny(communities.map { it.toTagIdOnly() }) + communities.map { it.toTagArray() },
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.removeAny(communities.map { it.toTagIdOnly() }) + communities.map { it.toTagArray() },
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: CommunityListEvent,
            community: CommunityTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.plus(community.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(community.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: CommunityListEvent,
            community: CommunityTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            return resign(
                privateTags = privateTags.remove(community.toTagIdOnly()),
                tags = earlierVersion.tags.remove(community.toTagIdOnly()),
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun resign(
            tags: TagArray,
            privateTags: TagArray,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = resign(
            content = PrivateTagsInContent.encryptNip44(privateTags, signer),
            tags = tags,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun resign(
            content: String,
            tags: TagArray,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            publicCommunities: List<CommunityTag> = emptyList(),
            privateCommunities: List<CommunityTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): CommunityListEvent {
            val template = build(publicCommunities, privateCommunities, signer, createdAt)
            return signer.sign(template)
        }

        suspend fun build(
            publicCommunities: List<CommunityTag> = emptyList(),
            privateCommunities: List<CommunityTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommunityListEvent>.() -> Unit = {},
        ) = eventTemplate<CommunityListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateCommunities.map { it.toTagArray() }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            communities(publicCommunities)

            initializer()
        }
    }
}
