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
package com.vitorpamplona.quartz.nip51Lists.hashtagList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.removeAny
import com.vitorpamplona.quartz.nip51Lists.removeIgnoreCase
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class HashtagListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun publicHashtags() = tags.mapNotNull(HashtagTag::parse)

    companion object {
        const val KIND = 10015
        const val ALT = "Hashtag List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun create(
            hashtag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = create(
            hashtags = listOf(hashtag),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun create(
            hashtags: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): HashtagListEvent =
            if (isPrivate) {
                create(
                    publicHashtags = emptyList(),
                    privateHashtags = hashtags,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicHashtags = hashtags,
                    privateHashtags = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: HashtagListEvent,
            hashtag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = add(
            earlierVersion = earlierVersion,
            hashtags = listOf(hashtag),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun add(
            earlierVersion: HashtagListEvent,
            hashtags: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): HashtagListEvent {
            val hashtags = hashtags.map { HashtagTag.assemble(it) }
            return if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.removeAny(hashtags) + hashtags,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.removeAny(hashtags) + hashtags,
                    signer = signer,
                    createdAt = createdAt,
                )
            }
        }

        suspend fun remove(
            earlierVersion: HashtagListEvent,
            hashtag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): HashtagListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            return resign(
                privateTags = privateTags.removeIgnoreCase(HashtagTag.assemble(hashtag)),
                tags = earlierVersion.tags.removeIgnoreCase(HashtagTag.assemble(hashtag)),
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
        ): HashtagListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            publicHashtags: List<String> = emptyList(),
            privateHashtags: List<String> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): HashtagListEvent {
            val template = build(publicHashtags, privateHashtags, signer, createdAt)
            return signer.sign(template)
        }

        fun create(
            publicHashtags: List<String> = emptyList(),
            privateHashtags: List<String> = emptyList(),
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): HashtagListEvent {
            val privateTagArray = publicHashtags.map { HashtagTag.assemble(it) }.toTypedArray()
            val publicTagArray = privateHashtags.map { HashtagTag.assemble(it) }.toTypedArray() + AltTag.assemble(ALT)
            return signer.signNip51List(createdAt, KIND, publicTagArray, privateTagArray)
        }

        suspend fun build(
            publicHashtags: List<String> = emptyList(),
            privateHashtags: List<String> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HashtagListEvent>.() -> Unit = {},
        ) = eventTemplate<HashtagListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateHashtags.map { HashtagTag.assemble(it) }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            hashtags(publicHashtags)

            initializer()
        }
    }
}
