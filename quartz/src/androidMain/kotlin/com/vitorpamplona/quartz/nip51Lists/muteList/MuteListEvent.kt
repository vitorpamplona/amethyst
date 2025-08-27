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
package com.vitorpamplona.quartz.nip51Lists.muteList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.plus

@Immutable
class MuteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(UserTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(UserTag::parseKey)

    fun countMutes() = tags.count(MuteTag::isTagged)

    fun publicMutes(): List<MuteTag> = tags.mapNotNull(MuteTag::parse)

    suspend fun privateMutes(signer: NostrSigner): List<MuteTag>? = privateTags(signer)?.mapNotNull(MuteTag::parse)

    override fun dTag() = FIXED_D_TAG

    companion object {
        const val KIND = 10000
        const val FIXED_D_TAG = ""
        const val ALT = "Mute List"

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        fun blockListFor(pubKeyHex: HexKey): String = "10000:$pubKeyHex:"

        suspend fun create(
            mute: MuteTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): MuteListEvent =
            if (isPrivate) {
                create(
                    publicMutes = emptyList(),
                    privateMutes = listOf(mute),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicMutes = listOf(mute),
                    privateMutes = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: MuteListEvent,
            mute: MuteTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): MuteListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    publicTags = earlierVersion.tags,
                    privateTags = privateTags.plus(mute.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.plus(mute.toTagArray()),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: MuteListEvent,
            mute: MuteTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): MuteListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

            return resign(
                privateTags = privateTags.remove(mute.toTagIdOnly()),
                publicTags = earlierVersion.tags.remove(mute.toTagIdOnly()),
                signer = signer,
                createdAt = createdAt,
            )
        }

        suspend fun resign(
            publicTags: TagArray,
            privateTags: TagArray,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = resign(
            content = PrivateTagsInContent.encryptNip44(privateTags, signer),
            tags = publicTags,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun resign(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): MuteListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            publicMutes: List<MuteTag> = emptyList(),
            privateMutes: List<MuteTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): MuteListEvent {
            val template = build(publicMutes, privateMutes, signer, createdAt)
            return signer.sign(template)
        }

        suspend fun build(
            publicMutes: List<MuteTag> = emptyList(),
            privateMutes: List<MuteTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MuteListEvent>.() -> Unit = {},
        ) = eventTemplate<MuteListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateMutes.map { it.toTagArray() }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            mutes(publicMutes)

            initializer()
        }
    }
}
