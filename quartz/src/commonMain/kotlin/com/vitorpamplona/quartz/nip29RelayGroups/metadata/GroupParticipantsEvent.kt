/*
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
package com.vitorpamplona.quartz.nip29RelayGroups.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Relay-signed live audio/video room presence (kind 39004). A group that
 * advertises the `livekit` metadata flag publishes this addressable event
 * (`d` = group id) listing the pubkeys currently connected to the AV room, one
 * per `["participant", "<pubkey>"]` tag. Updated by the relay as users join and
 * leave. This is an extension used by LiveKit-backed deployments (e.g. Armada);
 * it is not part of NIP-29 proper, but is parsed here for interop.
 */
@Immutable
class GroupParticipantsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun groupId() = dTag()

    fun participants(): List<HexKey> = tags.mapValueTagged(TAG_NAME) { it }

    companion object {
        const val KIND = 39004
        const val TAG_NAME = "participant"

        fun build(
            groupId: String,
            participantPubKeys: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupParticipantsEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(groupId)
            participantPubKeys.forEach { add(arrayOf(TAG_NAME, it)) }
            initializer()
        }
    }
}
