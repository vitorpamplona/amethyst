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
package com.vitorpamplona.quartz.nip17Dm.files

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.files.encryption.AESGCM
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionAlgo
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionKey
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionNonce
import com.vitorpamplona.quartz.nip17Dm.files.tags.FileTypeTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.OriginalHashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChatMessageEncryptedFileHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseDMGroupEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun replyTo() = tags.mapNotNull(ETag::parseId)

    fun url() = content

    fun mimeType() = tags.firstNotNullOfOrNull(FileTypeTag::parse)

    fun hash() = tags.firstNotNullOfOrNull(HashTag::parse)

    fun size() = tags.firstNotNullOfOrNull(SizeTag::parse)

    fun dimensions() = tags.firstNotNullOfOrNull(DimensionTag::parse)

    fun blurhash() = tags.firstNotNullOfOrNull(BlurhashTag::parse)

    fun originalHash() = tags.firstNotNullOfOrNull(OriginalHashTag::parse)

    fun algo() = tags.firstNotNullOfOrNull(EncryptionAlgo::parse)

    fun key() = tags.firstNotNullOfOrNull(EncryptionKey::parse)

    fun nonce() = tags.firstNotNullOfOrNull(EncryptionNonce::parse)

    companion object {
        const val KIND = 15
        const val ALT_DESCRIPTION = "Encrypted file in chat"

        fun build(
            to: List<PTag>,
            url: String,
            cipher: AESGCM,
            replyTo: EventHintBundle<ChatMessageEvent>? = null,
            mimeType: String? = null,
            hash: String? = null,
            size: Int? = null,
            dimension: DimensionTag? = null,
            blurhash: String? = null,
            originalHash: String? = null,
            magnetUri: String? = null,
            torrentInfoHash: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, url, createdAt) {
            alt(ALT_DESCRIPTION)

            group(to)

            encryptionAlgo(cipher.name())
            encryptionKey(cipher.keyBytes)
            encryptionNonce(cipher.nonce)

            replyTo?.let { reply(replyTo) }

            hash?.let { hash(it) }
            size?.let { fileSize(it) }
            mimeType?.let { mimeType(it) }
            dimension?.let { dimension(it) }
            blurhash?.let { blurhash(it) }
            originalHash?.let { originalHash(it) }
            magnetUri?.let { magnet(it) }
            torrentInfoHash?.let { torrentInfohash(it) }

            initializer()
        }
    }
}
