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
package com.vitorpamplona.quartz.experimental.ephemChat.list

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.tags.RoomIdTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.plus

@Immutable
class EphemeralChatListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var publicAndPrivateEventCache: Set<RoomId>? = null

    fun publicAndPrivateRoomIds(
        signer: NostrSigner,
        onReady: (Set<RoomId>) -> Unit,
    ) {
        publicAndPrivateEventCache?.let { eventList ->
            onReady(eventList)
            return
        }

        privateTags(signer) {
            val set = filterRooms(it)
            publicAndPrivateEventCache = set
            onReady(set)
        }
    }

    fun filterRooms(privateTags: Array<Array<String>>): Set<RoomId> {
        val privateRooms = privateTags.mapNotNull(RoomIdTag::parse)
        val publicRooms = tags.mapNotNull(RoomIdTag::parse)

        return (privateRooms + publicRooms).toSet()
    }

    companion object {
        const val KIND = 10023
        const val ALT = "Ephemeral Chat List"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        fun createRoom(
            room: RoomId,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EphemeralChatListEvent) -> Unit,
        ) {
            val tags = arrayOf(RoomIdTag.Companion.assemble(room))
            if (isPrivate) {
                PrivateTagsInContent.Companion.encryptNip04(
                    privateTags = tags,
                    signer = signer,
                ) { encryptedTags ->
                    create(encryptedTags, emptyArray(), signer, createdAt, onReady)
                }
            } else {
                create("", tags, signer, createdAt, onReady)
            }
        }

        fun removeRoom(
            earlierVersion: EphemeralChatListEvent,
            room: RoomId,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EphemeralChatListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.removeAll(
                earlierVersion,
                RoomIdTag.Companion.assemble(room.id, room.relayUrl),
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        fun addRoom(
            earlierVersion: EphemeralChatListEvent,
            room: RoomId,
            isPrivate: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EphemeralChatListEvent) -> Unit,
        ) {
            PrivateTagArrayBuilder.add(
                earlierVersion,
                RoomIdTag.Companion.assemble(room.id, room.relayUrl),
                isPrivate,
                signer,
            ) { encryptedContent, newTags ->
                create(encryptedContent, newTags, signer, createdAt, onReady)
            }
        }

        fun create(
            content: String,
            tags: Array<Array<String>>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EphemeralChatListEvent) -> Unit,
        ) {
            val newTags =
                if (tags.any { it.size > 1 && it[0] == "alt" }) {
                    tags
                } else {
                    tags + AltTag.Companion.assemble(ALT)
                }

            signer.sign(createdAt, KIND, newTags, content, onReady)
        }
    }
}
