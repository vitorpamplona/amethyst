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
package com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class LabeledBookmarkListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider {
    override fun eventHints() = tags.mapNotNull(EventBookmark::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(EventBookmark::parseId)

    override fun addressHints() = tags.mapNotNull(AddressBookmark::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(AddressBookmark::parseAddressId)

    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun nameOrTitle() = name() ?: title()

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun countBookmarks() = tags.count(BookmarkIdTag::isTagged)

    fun publicBookmarks(): List<BookmarkIdTag> = tags.mapNotNull(BookmarkIdTag::parse)

    suspend fun privateBookmarks(signer: NostrSigner): List<BookmarkIdTag>? = privateTags(signer)?.mapNotNull(BookmarkIdTag::parse)

    companion object {
        const val KIND = 30003

        const val ALT = "A labeled list of bookmarks"

        @OptIn(ExperimentalUuidApi::class)
        fun createBookmarkAddress(pubKey: HexKey) = Address(KIND, pubKey, Uuid.random().toString())

        suspend fun create(
            bookmarkIdTag: BookmarkIdTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): LabeledBookmarkListEvent =
            if (isPrivate) {
                create(
                    publicBookmarks = emptyList(),
                    privateBookmarks = listOf(bookmarkIdTag),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicBookmarks = listOf(bookmarkIdTag),
                    privateBookmarks = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: LabeledBookmarkListEvent,
            bookmarkIdTag: BookmarkIdTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): LabeledBookmarkListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.plus(bookmarkIdTag.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(bookmarkIdTag.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: LabeledBookmarkListEvent,
            bookmarkIdTag: BookmarkIdTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): LabeledBookmarkListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    privateTags = privateTags.remove(bookmarkIdTag.toTagIdOnly()),
                    tags = earlierVersion.tags,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.remove(bookmarkIdTag.toTagIdOnly()),
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
        ): LabeledBookmarkListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun create(
            name: String = "",
            publicBookmarks: List<BookmarkIdTag> = emptyList(),
            privateBookmarks: List<BookmarkIdTag> = emptyList(),
            dTag: String = Uuid.random().toString(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): LabeledBookmarkListEvent {
            val template = build(name, publicBookmarks, privateBookmarks, signer, dTag, createdAt)
            return signer.sign(template)
        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun build(
            name: String = "",
            publicBookmarks: List<BookmarkIdTag> = emptyList(),
            privateBookmarks: List<BookmarkIdTag> = emptyList(),
            signer: NostrSigner,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LabeledBookmarkListEvent>.() -> Unit = {},
        ) = eventTemplate(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateBookmarks.map { it.toTagArray() }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            dTag(dTag)
            alt(ALT)
            name(name)
            bookmarks(publicBookmarks)

            initializer()
        }
    }
}
