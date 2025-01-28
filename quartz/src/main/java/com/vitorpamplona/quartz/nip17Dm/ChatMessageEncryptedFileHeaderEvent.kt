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
package com.vitorpamplona.quartz.nip17Dm

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ChatMessageEncryptedFileHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    ChatroomKeyable,
    NIP17Group {
    /** Recipients intended to receive this conversation */
    fun recipientsPubKey() = tags.mapNotNull { if (it.size > 1 && it[0] == "p") it[1] else null }

    override fun groupMembers() = recipientsPubKey().plus(pubKey).toSet()

    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun talkingWith(oneSideHex: String): Set<HexKey> {
        val listedPubKeys = recipientsPubKey()

        val result =
            if (pubKey == oneSideHex) {
                listedPubKeys.toSet().minus(oneSideHex)
            } else {
                listedPubKeys.plus(pubKey).toSet().minus(oneSideHex)
            }

        if (result.isEmpty()) {
            // talking to myself
            return setOf(pubKey)
        }

        return result
    }

    override fun chatroomKey(toRemove: String): ChatroomKey = ChatroomKey(talkingWith(toRemove).toImmutableSet())

    fun url() = content

    fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == MIME_TYPE }?.get(1)

    fun alt() = tags.firstOrNull { it.size > 1 && it[0] == ALT }?.get(1)

    fun algo() = tags.firstOrNull { it.size > 1 && it[0] == ENCRYPTION_ALGORITHM }?.get(1)

    fun key() =
        tags
            .firstOrNull { it.size > 1 && it[0] == ENCRYPTION_KEY }
            ?.get(1)
            ?.runCatching { this.hexToByteArray() }
            ?.getOrNull()

    fun nonce() =
        tags
            .firstOrNull { it.size > 1 && it[0] == ENCRYPTION_NONCE }
            ?.get(1)
            ?.runCatching { this.hexToByteArray() }
            ?.getOrNull()

    fun hash() = tags.firstOrNull { it.size > 1 && it[0] == HASH }?.get(1)

    fun originalHash() = tags.firstOrNull { it.size > 1 && it[0] == ORIGINAL_HASH }?.get(1)

    fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)

    fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)?.let { Dimension.parse(it) }

    fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    companion object {
        const val KIND = 15
        const val ALT_DESCRIPTION = "Encrypted file in chat"

        const val MIME_TYPE = "file-type"

        const val ENCRYPTION_ALGORITHM = "encryption-algorithm"
        const val ENCRYPTION_KEY = "decryption-key"
        const val ENCRYPTION_NONCE = "decryption-nonce"

        const val FILE_SIZE = "size"
        const val DIMENSION = "dim"
        const val BLUR_HASH = "blurhash"
        const val HASH = "x"
        const val ORIGINAL_HASH = "ox"

        const val ALT = "alt"

        fun buildTags(
            to: List<HexKey>,
            repliesTo: List<HexKey>? = null,
            contentType: String?,
            algo: String,
            key: ByteArray,
            nonce: ByteArray? = null,
            originalHash: String? = null,
            hash: String? = null,
            size: Int? = null,
            dimensions: Dimension? = null,
            blurhash: String? = null,
            sensitiveContent: Boolean? = null,
            alt: String?,
        ): Array<Array<String>> {
            val repliesHex = repliesTo?.map { arrayOf("e", it) } ?: emptyList()

            return (
                to.map { arrayOf("p", it) } + repliesHex +
                    listOfNotNull(
                        contentType?.let { arrayOf(MIME_TYPE, it) },
                        arrayOf(ENCRYPTION_ALGORITHM, algo),
                        arrayOf(ENCRYPTION_KEY, key.toHexKey()),
                        nonce?.let { arrayOf(ENCRYPTION_NONCE, it.toHexKey()) },
                        alt?.ifBlank { null }?.let { arrayOf(ALT, it) } ?: arrayOf(ALT, ALT_DESCRIPTION),
                        originalHash?.let { arrayOf(ORIGINAL_HASH, it) },
                        hash?.let { arrayOf(HASH, it) },
                        size?.let { arrayOf(FILE_SIZE, it.toString()) },
                        dimensions?.let { arrayOf(DIMENSION, it.toString()) },
                        blurhash?.let { arrayOf(BLUR_HASH, it) },
                        sensitiveContent?.let {
                            if (it) {
                                ContentWarningSerializer.toTagArray()
                            } else {
                                null
                            }
                        },
                    )
            ).toTypedArray()
        }

        fun create(
            url: String,
            to: List<HexKey>,
            repliesTo: List<HexKey>? = null,
            contentType: String?,
            algo: String,
            key: ByteArray,
            nonce: ByteArray? = null,
            originalHash: String? = null,
            hash: String? = null,
            size: Int? = null,
            dimensions: Dimension? = null,
            blurhash: String? = null,
            sensitiveContent: Boolean? = null,
            alt: String?,
            signer: NostrSigner,
            isDraft: Boolean,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChatMessageEncryptedFileHeaderEvent) -> Unit,
        ) {
            val tags = buildTags(to, repliesTo, contentType, algo, key, nonce, originalHash, hash, size, dimensions, blurhash, sensitiveContent, alt)
            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags, url, onReady)
            } else {
                signer.sign(createdAt, KIND, tags, url, onReady)
            }
        }
    }
}
