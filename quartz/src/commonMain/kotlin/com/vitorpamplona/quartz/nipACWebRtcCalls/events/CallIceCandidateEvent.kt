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
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.callId

@Immutable
class CallIceCandidateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WebRTCEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun candidateJson() = content

    fun candidateSdp(): String = CANDIDATE_REGEX.find(content)?.groupValues?.get(1) ?: ""

    fun sdpMid(): String = SDP_MID_REGEX.find(content)?.groupValues?.get(1) ?: "0"

    fun sdpMLineIndex(): Int =
        SDP_MLINE_INDEX_REGEX
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull() ?: 0

    companion object {
        const val KIND = 25052
        const val ALT_DESCRIPTION = "WebRTC ICE candidate"

        private val CANDIDATE_REGEX = """"candidate"\s*:\s*"([^"]*)"""".toRegex()
        private val SDP_MID_REGEX = """"sdpMid"\s*:\s*"([^"]*)"""".toRegex()
        private val SDP_MLINE_INDEX_REGEX = """"sdpMLineIndex"\s*:\s*(\d+)""".toRegex()

        fun serializeCandidate(
            sdp: String,
            sdpMid: String,
            sdpMLineIndex: Int,
        ): String {
            val escapedSdp = sdp.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedMid = sdpMid.replace("\\", "\\\\").replace("\"", "\\\"")
            return """{"candidate":"$escapedSdp","sdpMid":"$escapedMid","sdpMLineIndex":$sdpMLineIndex}"""
        }

        fun build(
            candidateJson: String,
            peerPubKey: HexKey,
            callId: String,
            initializer: TagArrayBuilder<CallIceCandidateEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, candidateJson) {
            alt(ALT_DESCRIPTION)
            pTag(peerPubKey)
            callId(callId)
            initializer()
        }
    }
}
