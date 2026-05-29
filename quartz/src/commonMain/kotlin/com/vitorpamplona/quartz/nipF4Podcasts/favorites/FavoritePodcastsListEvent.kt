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
package com.vitorpamplona.quartz.nipF4Podcasts.favorites

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-F4 user favorite-podcasts list (`kind:10054`). Per the spec this advertises
 * the podcasts the user listens to as a soft public recommendation. Items are
 * `p` tags pointing at podcast pubkeys.
 *
 * Follows the NIP-51 convention of also allowing private items stored encrypted
 * in `content`, so a user can keep guilty pleasures off the public list while
 * still using the same event kind for their own subscription state.
 */
@Immutable
class FavoritePodcastsListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun publicFavorites() = tags.mapNotNull(UserTag::parse)

    suspend fun privateFavorites(signer: NostrSigner) = privateTags(signer)?.mapNotNull(UserTag::parse)

    companion object {
        const val KIND = 10054
        const val ALT = "Favorite podcasts list"

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, "")

        suspend fun create(
            publicFavorites: List<UserTag> = emptyList(),
            privateFavorites: List<UserTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoritePodcastsListEvent =
            resign(
                content = PrivateTagsInContent.encryptNip44(privateFavorites.map { it.toTagArray() }.toTypedArray(), signer),
                tags = publicFavorites.map { it.toTagArray() }.toTypedArray(),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun add(
            earlierVersion: FavoritePodcastsListEvent,
            podcast: UserTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoritePodcastsListEvent =
            if (isPrivate) {
                val privateTags =
                    earlierVersion.privateTags(signer)
                        ?: throw SignerExceptions.UnauthorizedDecryptionException()
                // MUST strip from public too — a podcast that was previously public and is now
                // being made private must not stay visible in the unencrypted tag list.
                resign(
                    tags = earlierVersion.tags.remove(podcast.toTagIdOnly()),
                    privateTags = privateTags.remove(podcast.toTagIdOnly()) + podcast.toTagArray(),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                // Symmetric: strip from private tags too when moving a previously-private
                // podcast to the public list, otherwise it sits in both halves.
                val privateTags = earlierVersion.privateTags(signer)
                if (privateTags != null) {
                    resign(
                        tags = earlierVersion.tags.remove(podcast.toTagIdOnly()) + podcast.toTagArray(),
                        privateTags = privateTags.remove(podcast.toTagIdOnly()),
                        signer = signer,
                        createdAt = createdAt,
                    )
                } else {
                    resign(
                        content = earlierVersion.content,
                        tags = earlierVersion.tags.remove(podcast.toTagIdOnly()) + podcast.toTagArray(),
                        signer = signer,
                        createdAt = createdAt,
                    )
                }
            }

        suspend fun remove(
            earlierVersion: FavoritePodcastsListEvent,
            podcast: UserTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoritePodcastsListEvent {
            val idOnly = podcast.toTagIdOnly()
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
        ): FavoritePodcastsListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }
            return signer.sign(createdAt, KIND, newTags, content)
        }
    }
}
