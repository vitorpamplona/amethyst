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
package com.vitorpamplona.quartz.nip51Lists.geohashList

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
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.removeAny
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GeohashListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun publicGeohashes() = tags.geohashList()

    suspend fun decryptPrivateGeohashes(signer: NostrSigner) = privateTags(signer)?.geohashList()

    suspend fun decryptGeohashes(signer: NostrSigner): List<String> = publicGeohashes() + (decryptPrivateGeohashes(signer) ?: emptyList())

    companion object {
        const val KIND = 10081
        const val ALT = "Geohash List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun create(
            geohash: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = create(
            geohashes = listOf(geohash),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun create(
            geohashes: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GeohashListEvent =
            if (isPrivate) {
                create(
                    publicGeohashes = emptyList(),
                    privateGeohashes = geohashes,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicGeohashes = geohashes,
                    privateGeohashes = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: GeohashListEvent,
            geohash: String,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = add(
            earlierVersion = earlierVersion,
            geohashes = listOf(geohash),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun add(
            earlierVersion: GeohashListEvent,
            geohashes: List<String>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GeohashListEvent {
            val geohashTags = geohashes.map { GeoHashTag.assembleSingle(it) }
            return if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.removeAny(geohashTags) + geohashTags,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.removeAny(geohashTags) + geohashTags,
                    signer = signer,
                    createdAt = createdAt,
                )
            }
        }

        suspend fun remove(
            earlierVersion: GeohashListEvent,
            geohash: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GeohashListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            return resign(
                privateTags = privateTags.remove(GeoHashTag.assembleSingle(geohash)),
                tags = earlierVersion.tags.remove(GeoHashTag.assembleSingle(geohash)),
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
        ): GeohashListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            publicGeohashes: List<String> = emptyList(),
            privateGeohashes: List<String> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GeohashListEvent {
            val template = build(publicGeohashes, privateGeohashes, signer, createdAt)
            return signer.sign(template)
        }

        fun create(
            publicGeohashes: List<String> = emptyList(),
            privateGeohashes: List<String> = emptyList(),
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): GeohashListEvent {
            val privateTagArray = publicGeohashes.map { GeoHashTag.assembleSingle(it) }.toTypedArray()
            val publicTagArray = privateGeohashes.map { GeoHashTag.assembleSingle(it) }.toTypedArray() + AltTag.assemble(ALT)
            return signer.signNip51List(createdAt, KIND, publicTagArray, privateTagArray)
        }

        suspend fun build(
            publicGeohashes: List<String> = emptyList(),
            privateGeohashes: List<String> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeohashListEvent>.() -> Unit = {},
        ) = eventTemplate<GeohashListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateGeohashes.map { GeoHashTag.assembleSingle(it) }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            geohashes(publicGeohashes)

            initializer()
        }
    }
}
