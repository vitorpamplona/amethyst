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
package com.vitorpamplona.quartz.nipACWebRtcCalls.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTagIds
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallIdTag
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.callId
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class CallAnswerEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun callId() = tags.firstNotNullOfOrNull(CallIdTag::parse)

    fun sdpAnswer() = content

    /** All pubkeys referenced by `p` tags in this event. */
    fun recipientPubKeys(): Set<HexKey> = tags.mapNotNull(PTag::parseKey).toSet()

    /** All group members: `p`-tagged pubkeys plus the event author. */
    fun groupMembers(): Set<HexKey> = recipientPubKeys().plus(pubKey)

    companion object {
        const val KIND = 25051
        const val ALT_DESCRIPTION = "WebRTC call answer"
        const val EXPIRATION_SECONDS = 20L

        fun build(
            sdpAnswer: String,
            callerPubKey: HexKey,
            callId: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CallAnswerEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, sdpAnswer, createdAt) {
            alt(ALT_DESCRIPTION)
            pTag(callerPubKey)
            callId(callId)
            expiration(createdAt + EXPIRATION_SECONDS)
            initializer()
        }

        fun build(
            sdpAnswer: String,
            memberPubKeys: Set<HexKey>,
            callId: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CallAnswerEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, sdpAnswer, createdAt) {
            alt(ALT_DESCRIPTION)
            pTagIds(memberPubKeys)
            callId(callId)
            expiration(createdAt + EXPIRATION_SECONDS)
            initializer()
        }
    }
}
