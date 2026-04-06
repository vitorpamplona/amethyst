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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTagIds
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallTypeTag
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.callId
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.callType

@Immutable
class CallOfferEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WebRTCEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun callType() = tags.firstNotNullOfOrNull(CallTypeTag::parse)

    fun sdpOffer() = content

    /** All pubkeys referenced by `p` tags in this offer. */
    fun recipientPubKeys(): Set<HexKey> = tags.mapNotNull(PTag::parseKey).toSet()

    /**
     * All group members for this call: the `p`-tagged recipients plus the
     * event author (caller).  For 1-to-1 calls the set has two elements.
     */
    fun groupMembers(): Set<HexKey> = recipientPubKeys().plus(pubKey)

    /** True when this offer targets more than one callee. */
    fun isGroupCall(): Boolean = recipientPubKeys().size > 1

    companion object {
        const val KIND = 25050
        const val ALT_DESCRIPTION = "WebRTC call offer"

        fun build(
            sdpOffer: String,
            calleePubKey: HexKey,
            callId: String,
            type: CallType,
            initializer: TagArrayBuilder<CallOfferEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, sdpOffer) {
            alt(ALT_DESCRIPTION)
            pTag(calleePubKey)
            callId(callId)
            callType(type)
            initializer()
        }

        fun build(
            sdpOffer: String,
            calleePubKeys: Set<HexKey>,
            callId: String,
            type: CallType,
            initializer: TagArrayBuilder<CallOfferEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, sdpOffer) {
            alt(ALT_DESCRIPTION)
            pTagIds(calleePubKeys)
            callId(callId)
            callType(type)
            initializer()
        }
    }
}
