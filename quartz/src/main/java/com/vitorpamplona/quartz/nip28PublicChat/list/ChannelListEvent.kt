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
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHintOptional
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.map

@Immutable
class ChannelListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var publicAndPrivateEventCache: Set<EventIdHint>? = null

    fun publicAndPrivateChannels(
        signer: NostrSigner,
        onReady: (Set<EventIdHint>) -> Unit,
    ) {
        publicAndPrivateEventCache?.let { eventList ->
            onReady(eventList)
            return
        }

        mergeTagList(signer) {
            val set = it.mapNotNull(ETag::parseAsHint).toSet()
            publicAndPrivateEventCache = set
            onReady(set)
        }
    }

    companion object {
        const val KIND = 10005
        const val ALT = "Public Chat List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        private fun createChannelBase(
            tags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.create(
                tags,
                isPrivate,
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        fun createChannel(
            channel: EventHintBundle<ChannelCreateEvent>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) = createChannelBase(
            tags = arrayOf(ETag.assemble(channel.event.id, channel.relay, channel.event.pubKey)),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun createChannel(
            channel: EventIdHintOptional,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) = createChannelBase(
            tags = arrayOf(ETag.assemble(channel.eventId, channel.relay, null)),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun createChannels(
            channels: List<EventIdHintOptional>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) = createChannelBase(
            tags = channels.map { ETag.assemble(it.eventId, it.relay, null) }.toTypedArray(),
            isPrivate = isPrivate,
            signer = signer,
            createdAt = createdAt,
            onReady = onReady,
        )

        fun removeChannel(
            earlierVersion: ChannelListEvent,
            channel: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.removeAll(
                earlierVersion,
                ETag.assemble(channel, null, null),
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        private fun addChannelBase(
            earlierVersion: ChannelListEvent,
            newTags: Array<Array<String>>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
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

        fun addChannel(
            earlierVersion: ChannelListEvent,
            channel: EventHintBundle<ChannelCreateEvent>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) = addChannelBase(
            earlierVersion,
            arrayOf(ETag.assemble(channel.event.id, channel.relay, channel.event.pubKey)),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        fun addChannel(
            earlierVersion: ChannelListEvent,
            channel: EventIdHintOptional,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) = addChannelBase(
            earlierVersion,
            arrayOf(ETag.assemble(channel.eventId, channel.relay, null)),
            isPrivate,
            signer,
            createdAt,
            onReady,
        )

        fun addChannels(
            earlierVersion: ChannelListEvent,
            channels: List<EventIdHintOptional>,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelListEvent) -> Unit,
        ) = addChannelBase(
            earlierVersion,
            channels.map { ETag.assemble(it.eventId, it.relay, null) }.toTypedArray(),
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
            onReady: (ChannelListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.Companion.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }

        fun create(
            list: List<EventIdHint>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ChannelListEvent? {
            val tags = list.map { ETag.assemble(it.eventId, it.relay, null) }.toTypedArray()
            return signer.sign(createdAt, KIND, tags, "")
        }
    }
}
