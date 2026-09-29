/*
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
package com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.diff.DiffableEvent
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-51 kind 10021, "Favorite follow sets": `a` pointers to kind:30000 follow sets, the
 * user's own or anyone else's, that the user wants to keep at hand (e.g. as feeds).
 * Like the other standard lists, entries can be public tags or NIP-44 private tags.
 */
@Immutable
class FavoriteFollowSetsListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    DiffableEvent<FavoriteFollowSetsListDiff>,
    LinkProvider {
    // A plain replaceable (10000..19999): a stray `d` tag must not split its address.
    override fun dTag() = FIXED_D_TAG

    override fun diffFrom(older: Event): FavoriteFollowSetsListDiff? {
        if (older !is FavoriteFollowSetsListEvent || older.pubKey != pubKey || older.dTag() != dTag()) return null
        return FavoriteFollowSetsListDiff(
            ListDiff.of(older.publicFavoriteFollowSets(), publicFavoriteFollowSets(), { it.address }, { a, b -> a.relayHint == b.relayHint }),
            privateItemsChangeFrom(older),
        )
    }

    fun publicFavoriteFollowSets(): List<AddressBookmark> = tags.favoriteFollowSetBookmarks()

    suspend fun privateFavoriteFollowSets(signer: NostrSigner): List<AddressBookmark>? = privateTags(signer)?.favoriteFollowSetBookmarks()

    /** NIP-51 kind 10021: the follow sets (kind 30000 `a` tags) the user favorited; other `a` kinds are skipped, as [publicFavoriteFollowSets] does. */
    override fun links(): List<Link<*>> = links { publicFavoriteFollowSets().forEach { address(Relation.FAVORITE, it, AddressBookmark.TAG_NAME) } }

    companion object {
        const val KIND = 10021
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun create(
            followSet: AddressBookmark,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteFollowSetsListEvent =
            if (isPrivate) {
                create(publicFollowSets = emptyList(), privateFollowSets = listOf(followSet), signer = signer, createdAt = createdAt)
            } else {
                create(publicFollowSets = listOf(followSet), privateFollowSets = emptyList(), signer = signer, createdAt = createdAt)
            }

        suspend fun add(
            earlierVersion: FavoriteFollowSetsListEvent,
            followSet: AddressBookmark,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteFollowSetsListEvent =
            if (isPrivate) {
                val privateTags =
                    earlierVersion.privateTags(signer)
                        ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.remove(followSet.toTagIdOnly()) + followSet.toTagArray(),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.remove(followSet.toTagIdOnly()) + followSet.toTagArray(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: FavoriteFollowSetsListEvent,
            followSet: Address,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteFollowSetsListEvent {
            val idOnly = AddressBookmark.assemble(followSet, null)
            val privateTags = earlierVersion.privateTags(signer)
            return if (privateTags != null) {
                resign(
                    privateTags = privateTags.remove(idOnly),
                    tags = earlierVersion.tags.remove(idOnly),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.remove(idOnly),
                    signer = signer,
                    createdAt = createdAt,
                )
            }
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
        ): FavoriteFollowSetsListEvent = signer.sign(createdAt, KIND, tags, content)

        suspend fun create(
            publicFollowSets: List<AddressBookmark> = emptyList(),
            privateFollowSets: List<AddressBookmark> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteFollowSetsListEvent = signer.sign(build(publicFollowSets, privateFollowSets, signer, createdAt))

        suspend fun build(
            publicFollowSets: List<AddressBookmark> = emptyList(),
            privateFollowSets: List<AddressBookmark> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<FavoriteFollowSetsListEvent>.() -> Unit = {},
        ) = eventTemplate<FavoriteFollowSetsListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateFollowSets.map { it.toTagArray() }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            favoriteFollowSets(publicFollowSets)

            initializer()
        }
    }
}
