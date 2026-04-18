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
package com.vitorpamplona.quartz.nip51Lists.favoriteDvmList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class FavoriteDvmListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun publicFavoriteDvms(): List<AddressBookmark> = tags.mapNotNull(AddressBookmark::parse)

    suspend fun privateFavoriteDvms(signer: NostrSigner): List<AddressBookmark>? = privateTags(signer)?.mapNotNull(AddressBookmark::parse)

    companion object {
        const val KIND = 10090
        const val ALT = "Favorite DVM list"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun create(
            dvm: AddressBookmark,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteDvmListEvent =
            if (isPrivate) {
                create(
                    publicDvms = emptyList(),
                    privateDvms = listOf(dvm),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicDvms = listOf(dvm),
                    privateDvms = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: FavoriteDvmListEvent,
            dvm: AddressBookmark,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteDvmListEvent =
            if (isPrivate) {
                val privateTags =
                    earlierVersion.privateTags(signer)
                        ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.remove(dvm.toTagIdOnly()) + dvm.toTagArray(),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.remove(dvm.toTagIdOnly()) + dvm.toTagArray(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: FavoriteDvmListEvent,
            dvm: Address,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteDvmListEvent {
            val idOnly = AddressBookmark.assemble(dvm, null)
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
        ): FavoriteDvmListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            publicDvms: List<AddressBookmark> = emptyList(),
            privateDvms: List<AddressBookmark> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): FavoriteDvmListEvent {
            val template = build(publicDvms, privateDvms, signer, createdAt)
            return signer.sign(template)
        }

        suspend fun build(
            publicDvms: List<AddressBookmark> = emptyList(),
            privateDvms: List<AddressBookmark> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<FavoriteDvmListEvent>.() -> Unit = {},
        ) = eventTemplate<FavoriteDvmListEvent>(
            kind = KIND,
            description =
                PrivateTagsInContent.encryptNip44(
                    privateDvms.map { it.toTagArray() }.toTypedArray(),
                    signer,
                ),
            createdAt = createdAt,
        ) {
            alt(ALT)
            favoriteDvms(publicDvms)

            initializer()
        }
    }
}
