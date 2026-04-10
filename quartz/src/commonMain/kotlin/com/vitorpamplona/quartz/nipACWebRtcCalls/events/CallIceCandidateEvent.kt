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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Immutable
class CallIceCandidateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WebRTCEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    private val parsedJson by lazy {
        try {
            Json.parseToJsonElement(content).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun candidateJson() = content

    fun candidateSdp(): String = parsedJson?.get("candidate")?.jsonPrimitive?.content ?: ""

    fun sdpMid(): String = parsedJson?.get("sdpMid")?.jsonPrimitive?.content ?: "0"

    fun sdpMLineIndex(): Int =
        try {
            parsedJson?.get("sdpMLineIndex")?.jsonPrimitive?.int ?: 0
        } catch (_: Exception) {
            0
        }

    companion object {
        const val KIND = 25052
        const val ALT_DESCRIPTION = "WebRTC ICE candidate"

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
