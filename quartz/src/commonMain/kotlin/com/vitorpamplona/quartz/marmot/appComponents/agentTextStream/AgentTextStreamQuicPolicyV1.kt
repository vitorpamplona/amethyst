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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream

import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.mls.components.ComponentData

/**
 * `marmot.group.agent-text-stream.quic.v1` (component `0x8006`).
 *
 * A group carrying this component streams an agent's text as it is produced,
 * over QUIC, out of band from the kind-445 group timeline; a kind-1200 event
 * anchors each stream and a kind-9 chat carries its final transcript. The
 * component itself is only the group's POLICY for those streams: which member
 * roles are required and allowed, and the record bounds every participant
 * enforces.
 *
 * Twelve bytes, fixed:
 *
 * ```
 * required_member_roles   uint8
 * allowed_member_roles    uint8
 * max_plaintext_frame_len uint32
 * replay_ttl_secs         uint32
 * padding_bucket_bytes    uint16
 * ```
 *
 * The role masks are the reason this component cannot be treated as opaque
 * bytes. `required_member_roles` names MLS leaf capabilities every member MUST
 * advertise ([AgentTextStreamRoles.capabilityFor]), so a group requiring
 * `receive` refuses to add a leaf that does not advertise `0xF2D1` — which is
 * exactly how an implementation that ignores this component gets locked out of
 * every group that carries it.
 */
class AgentTextStreamQuicPolicyV1(
    val requiredMemberRoles: Int,
    val allowedMemberRoles: Int,
    val maxPlaintextFrameLen: Long,
    val replayTtlSecs: Long,
    val paddingBucketBytes: Int,
) {
    init {
        require(requiredMemberRoles != 0) { "required agent text stream roles cannot be empty" }
        require(requiredMemberRoles and AgentTextStreamRoles.MASK.inv() == 0) {
            "required agent text stream role mask contains unknown bits"
        }
        require(allowedMemberRoles and AgentTextStreamRoles.MASK.inv() == 0) {
            "allowed agent text stream role mask contains unknown bits"
        }
        require(requiredMemberRoles and allowedMemberRoles.inv() == 0) {
            "required agent text stream roles must be a subset of allowed roles"
        }
        require(maxPlaintextFrameLen > 0) { "agent text stream plaintext frame limit cannot be zero" }
        require(maxPlaintextFrameLen <= MAX_PLAINTEXT_FRAME_LEN) {
            "agent text stream plaintext frame limit exceeds the app profile max"
        }
        require(replayTtlSecs in 0..MAX_REPLAY_TTL_SECS) {
            "agent text stream replay ttl exceeds the app profile max"
        }
        require(paddingBucketBytes in 0..MAX_PADDING_BUCKET_BYTES) {
            "agent text stream padding bucket exceeds the app profile max"
        }
    }

    fun requires(role: Int) = requiredMemberRoles and role != 0

    fun allows(role: Int) = allowedMemberRoles and role != 0

    /** The MLS leaf capabilities this policy demands of every member. */
    fun requiredRoleCapabilities(): List<Int> = AgentTextStreamRoles.capabilitiesFor(requiredMemberRoles)

    fun encode(): ByteArray {
        val out = ByteArray(STATE_LENGTH)
        out[0] = requiredMemberRoles.toByte()
        out[1] = allowedMemberRoles.toByte()
        out[2] = (maxPlaintextFrameLen shr 24).toByte()
        out[3] = (maxPlaintextFrameLen shr 16).toByte()
        out[4] = (maxPlaintextFrameLen shr 8).toByte()
        out[5] = maxPlaintextFrameLen.toByte()
        out[6] = (replayTtlSecs shr 24).toByte()
        out[7] = (replayTtlSecs shr 16).toByte()
        out[8] = (replayTtlSecs shr 8).toByte()
        out[9] = replayTtlSecs.toByte()
        out[10] = (paddingBucketBytes shr 8).toByte()
        out[11] = paddingBucketBytes.toByte()
        return out
    }

    fun toComponentData() = ComponentData(COMPONENT_ID, encode())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentTextStreamQuicPolicyV1) return false
        return requiredMemberRoles == other.requiredMemberRoles &&
            allowedMemberRoles == other.allowedMemberRoles &&
            maxPlaintextFrameLen == other.maxPlaintextFrameLen &&
            replayTtlSecs == other.replayTtlSecs &&
            paddingBucketBytes == other.paddingBucketBytes
    }

    override fun hashCode(): Int {
        var result = requiredMemberRoles
        result = 31 * result + allowedMemberRoles
        result = 31 * result + maxPlaintextFrameLen.hashCode()
        result = 31 * result + replayTtlSecs.hashCode()
        result = 31 * result + paddingBucketBytes
        return result
    }

    companion object {
        const val COMPONENT_ID = AppComponentIds.AGENT_TEXT_STREAM_QUIC_V1

        /** Fixed encoding: 1 + 1 + 4 + 4 + 2. */
        const val STATE_LENGTH = 12

        /**
         * App-profile cap on one frame's plaintext. 65519 keeps the ciphertext
         * (plaintext + the 16-byte AEAD tag) inside the record's
         * `ciphertext<0..2^16-1>` wire field.
         */
        const val MAX_PLAINTEXT_FRAME_LEN = 65519L
        const val MAX_REPLAY_TTL_SECS = 5L * 60L
        const val MAX_PADDING_BUCKET_BYTES = 4096

        /**
         * The policy a user-to-agent group installs: every member must be able
         * to receive, and a member may additionally send.
         */
        fun userToAgentDefault() =
            AgentTextStreamQuicPolicyV1(
                requiredMemberRoles = AgentTextStreamRoles.RECEIVE,
                allowedMemberRoles = AgentTextStreamRoles.RECEIVE or AgentTextStreamRoles.SEND,
                maxPlaintextFrameLen = 4096,
                replayTtlSecs = 0,
                paddingBucketBytes = 0,
            )

        /**
         * Decode exactly [STATE_LENGTH] bytes. A short, long or out-of-range
         * payload is rejected rather than defaulted: these bytes live in signed
         * group state, and a receiver that guessed a role mask would silently
         * admit a member the group refuses.
         */
        fun decode(bytes: ByteArray): AgentTextStreamQuicPolicyV1 {
            require(bytes.size == STATE_LENGTH) {
                "agent text stream component state must be $STATE_LENGTH bytes, got ${bytes.size}"
            }

            fun u32(at: Int): Long =
                ((bytes[at].toLong() and 0xff) shl 24) or
                    ((bytes[at + 1].toLong() and 0xff) shl 16) or
                    ((bytes[at + 2].toLong() and 0xff) shl 8) or
                    (bytes[at + 3].toLong() and 0xff)

            return AgentTextStreamQuicPolicyV1(
                requiredMemberRoles = bytes[0].toInt() and 0xff,
                allowedMemberRoles = bytes[1].toInt() and 0xff,
                maxPlaintextFrameLen = u32(2),
                replayTtlSecs = u32(6),
                paddingBucketBytes = ((bytes[10].toInt() and 0xff) shl 8) or (bytes[11].toInt() and 0xff),
            )
        }

        fun decodeOrNull(bytes: ByteArray): AgentTextStreamQuicPolicyV1? =
            try {
                decode(bytes)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

/**
 * The three agent-text-stream roles, and the MLS leaf capability that backs
 * each one.
 *
 * Each role is a distinct private-use MLS extension type rather than one flag
 * on the component, so a member can advertise `receive` without claiming
 * `send` or `fanout`. That is what makes `required_member_roles` enforceable
 * per role: MLS itself refuses a leaf that does not advertise a required
 * extension, so the group's policy is checked by the CGKA rather than by the
 * application.
 */
object AgentTextStreamRoles {
    const val RECEIVE = 0x01
    const val SEND = 0x02
    const val FANOUT = 0x04
    const val MASK = RECEIVE or SEND or FANOUT

    /** MLS leaf capability (extension type) for each role. */
    const val RECEIVE_CAPABILITY = 0xF2D1
    const val SEND_CAPABILITY = 0xF2D2
    const val FANOUT_CAPABILITY = 0xF2D4

    fun capabilityFor(role: Int): Int =
        when (role) {
            RECEIVE -> RECEIVE_CAPABILITY
            SEND -> SEND_CAPABILITY
            FANOUT -> FANOUT_CAPABILITY
            else -> throw IllegalArgumentException("unknown agent text stream role $role")
        }

    /**
     * Capabilities a role mask demands, in role order. Unknown bits are
     * ignored here — the component decoder rejects them, so a mask that
     * reaches this function has already been validated.
     */
    fun capabilitiesFor(mask: Int): List<Int> =
        buildList {
            if (mask and RECEIVE != 0) add(RECEIVE_CAPABILITY)
            if (mask and SEND != 0) add(SEND_CAPABILITY)
            if (mask and FANOUT != 0) add(FANOUT_CAPABILITY)
        }
}
