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
package com.vitorpamplona.quartz.nip51Lists.interests

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlin.coroutines.resume

@Immutable
class HashtagListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var publicAndPrivateHashtagCache: Set<String>? = null

    fun publicHashtags() = tags.mapNotNull(HashtagTag::parse)

    fun publicAndPrivateHashtag(
        signer: NostrSigner,
        onReady: (Set<String>) -> Unit,
    ) {
        publicAndPrivateHashtagCache?.let { eventList ->
            onReady(eventList)
            return
        }

        mergeTagList(signer) {
            val set = it.mapNotNull(HashtagTag::parse).toSet()
            publicAndPrivateHashtagCache = set
            onReady(set)
        }
    }

    suspend fun publicAndPrivateHashtag(signer: NostrSigner): Set<String>? {
        publicAndPrivateHashtagCache?.let { return it }

        return tryAndWait { continuation ->
            publicAndPrivateHashtag(signer) { privateTagList ->
                continuation.resume(privateTagList)
            }
        }
    }

    companion object {
        const val KIND = 10015
        const val ALT = "Hashtag List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        private fun createHashtagBase(
            tags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.create(
                tags,
                isPrivate,
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        fun createHashtag(
            hashtag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
        ) = createHashtagBase(
            tags = arrayOf(HashtagTag.assemble(hashtag)),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun createHashtags(
            hashtags: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
        ) = createHashtagBase(
            tags = HashtagTag.assemble(hashtags).toTypedArray(),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun removeHashtag(
            earlierVersion: HashtagListEvent,
            hashtag: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.removeAll(
                earlierVersion,
                HashtagTag.assemble(hashtag),
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        private fun addHashtagBase(
            earlierVersion: HashtagListEvent,
            newTags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
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

        fun addHashtag(
            earlierVersion: HashtagListEvent,
            hashtag: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
        ) = addHashtagBase(
            earlierVersion,
            arrayOf(HashtagTag.assemble(hashtag)),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        fun addHashtags(
            earlierVersion: HashtagListEvent,
            hashtags: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HashtagListEvent) -> Unit,
        ) = addHashtagBase(
            earlierVersion,
            HashtagTag.assemble(hashtags).toTypedArray(),
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
            onReady: (HashtagListEvent) -> Unit,
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
            hashtags: List<String>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): HashtagListEvent? {
            val tags = HashtagTag.assemble(hashtags).toTypedArray()
            return signer.sign(createdAt, KIND, tags, "")
        }
    }
}
