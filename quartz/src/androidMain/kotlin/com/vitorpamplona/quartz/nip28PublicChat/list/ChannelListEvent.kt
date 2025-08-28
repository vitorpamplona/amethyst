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
package com.vitorpamplona.quartz.nip28PublicChat.list

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip28PublicChat.list.tags.ChannelTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.encryption.signNip51List
import com.vitorpamplona.quartz.nip51Lists.removeAny
import com.vitorpamplona.quartz.nip51Lists.removeParsing
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    override fun eventHints() = tags.mapNotNull(ChannelTag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ChannelTag::parseId)

    companion object {
        const val KIND = 10005
        const val ALT = "Public Chat List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        suspend fun create(
            channel: ChannelTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = create(
            channels = listOf(channel),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun create(
            channels: List<ChannelTag>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ChannelListEvent =
            if (isPrivate) {
                create(
                    publicChannels = emptyList(),
                    privateChannels = channels,
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                create(
                    publicChannels = channels,
                    privateChannels = emptyList(),
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun add(
            earlierVersion: ChannelListEvent,
            channel: ChannelTag,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = add(
            earlierVersion = earlierVersion,
            channels = listOf(channel),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
        )

        suspend fun add(
            earlierVersion: ChannelListEvent,
            channels: List<ChannelTag>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ChannelListEvent =
            if (isPrivate) {
                val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                resign(
                    tags = earlierVersion.tags,
                    privateTags = privateTags.removeAny(channels.map { it.toTagIdOnly() }) + channels.map { it.toTagArray() },
                    signer = signer,
                    createdAt = createdAt,
                )
            } else {
                resign(
                    content = earlierVersion.content,
                    tags = earlierVersion.tags.removeAny(channels.map { it.toTagIdOnly() }) + channels.map { it.toTagArray() },
                    signer = signer,
                    createdAt = createdAt,
                )
            }

        suspend fun remove(
            earlierVersion: ChannelListEvent,
            channel: ChannelTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ChannelListEvent {
            val privateTags = earlierVersion.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            return resign(
                privateTags = privateTags.removeParsing(ChannelTag::parseId, channel.eventId),
                tags = earlierVersion.tags.removeParsing(ChannelTag::parseId, channel.eventId),
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
        ): ChannelListEvent {
            val newTags =
                if (tags.fastAny(AltTag::match)) {
                    tags
                } else {
                    tags + AltTag.assemble(ALT)
                }

            return signer.sign(createdAt, KIND, newTags, content)
        }

        suspend fun create(
            publicChannels: List<ChannelTag> = emptyList(),
            privateChannels: List<ChannelTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ChannelListEvent {
            val template = build(publicChannels, privateChannels, signer, createdAt)
            return signer.sign(template)
        }

        fun create(
            publicChannels: List<ChannelTag> = emptyList(),
            privateChannels: List<ChannelTag> = emptyList(),
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ChannelListEvent {
            val privateTagArray = privateChannels.map { it.toTagArray() }.toTypedArray()
            val publicTagArray = publicChannels.map { it.toTagArray() }.toTypedArray() + AltTag.assemble(ALT)
            return signer.signNip51List(createdAt, KIND, publicTagArray, privateTagArray)
        }

        suspend fun build(
            publicChannels: List<ChannelTag> = emptyList(),
            privateChannels: List<ChannelTag> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelListEvent>.() -> Unit = {},
        ) = eventTemplate<ChannelListEvent>(
            kind = KIND,
            description = PrivateTagsInContent.encryptNip44(privateChannels.map { it.toTagArray() }.toTypedArray(), signer),
            createdAt = createdAt,
        ) {
            alt(ALT)
            channels(publicChannels)

            initializer()
        }
    }
}
