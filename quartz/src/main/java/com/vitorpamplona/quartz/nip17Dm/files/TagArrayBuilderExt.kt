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

import com.vitorpamplona.quartz.nip01Core.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionAlgo
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionKey
import com.vitorpamplona.quartz.nip17Dm.files.tags.EncryptionNonce
import com.vitorpamplona.quartz.nip17Dm.files.tags.FileTypeTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.FallbackTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MagnetTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.OriginalHashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ServiceTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SummaryTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.TorrentInfoHash

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.reply(msg: MarkedETag) = add(msg.toTagArray())

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.reply(msg: EventHintBundle<ChatMessageEvent>) = reply(msg.toMarkedETag(MarkedETag.MARKER.REPLY))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.group(list: List<PTag>) = pTags(list)

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.group(pubkey: PTag) = pTag(pubkey)

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.encryptionAlgo(algo: String) = add(EncryptionAlgo.assemble(algo))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.encryptionKey(key: ByteArray) = add(EncryptionKey.assemble(key))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.encryptionNonce(nonce: ByteArray) = add(EncryptionNonce.assemble(nonce))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.mimeType(mimeType: String) = add(FileTypeTag.assemble(mimeType))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.hash(hash: HexKey) = add(HashTag.assemble(hash))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.fileSize(size: Int) = add(SizeTag.assemble(size))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.dimension(dim: DimensionTag) = add(DimensionTag.assemble(dim))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.blurhash(blurhash: String) = add(BlurhashTag.assemble(blurhash))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.originalHash(hash: HexKey) = add(OriginalHashTag.assemble(hash))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.torrentInfohash(hash: String) = add(TorrentInfoHash.assemble(hash))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.magnet(magnetUri: String) = add(MagnetTag.assemble(magnetUri))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.image(imageUrl: HexKey) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.thumb(trumbUrl: HexKey) = add(ThumbTag.assemble(trumbUrl))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.summary(summary: HexKey) = add(SummaryTag.assemble(summary))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.fallback(fallbackUrl: HexKey) = add(FallbackTag.assemble(fallbackUrl))

fun TagArrayBuilder<ChatMessageEncryptedFileHeaderEvent>.service(service: HexKey) = add(ServiceTag.assemble(service))
