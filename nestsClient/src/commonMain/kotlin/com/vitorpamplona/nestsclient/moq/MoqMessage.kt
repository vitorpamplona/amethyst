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
package com.vitorpamplona.nestsclient.moq

/**
 * Subset of the MoQ-transport control-plane messages needed for a listener
 * talking to a nests server. Per draft-ietf-moq-transport, control messages
 * on the control stream share the wire layout:
 *
 *   message_type (varint) | message_length (varint) | payload...
 *
 * This phase (3c-1) covers only the setup handshake. SUBSCRIBE / ANNOUNCE /
 * OBJECT messages arrive in Phase 3c-2.
 */
sealed class MoqMessage {
    abstract val type: MoqMessageType
}

/**
 * Message type codes per draft-ietf-moq-transport-17. Held as enum so future
 * draft revisions can extend without breaking call sites.
 */
enum class MoqMessageType(
    val code: Long,
) {
    ClientSetup(0x40),
    ServerSetup(0x41),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqMessageType? = byCode[code]
    }
}

/**
 * Wire-level setup parameter: key is a varint, value is an opaque byte string
 * (MoQ doesn't prescribe a single type for values — some draft revisions use
 * varints, others length-prefixed bytes; nests uses bytes in either case).
 */
data class SetupParameter(
    val key: Long,
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SetupParameter) return false
        return key == other.key && value.contentEquals(other.value)
    }

    override fun hashCode(): Int = 31 * key.hashCode() + value.contentHashCode()

    companion object {
        /** ROLE parameter (key 0x00), draft-specific values. Kept as bytes. */
        const val KEY_ROLE: Long = 0x00L

        /** PATH parameter (key 0x01). */
        const val KEY_PATH: Long = 0x01L

        /** MAX_SUBSCRIBE_ID (key 0x02 in most drafts). */
        const val KEY_MAX_SUBSCRIBE_ID: Long = 0x02L
    }
}

/**
 * CLIENT_SETUP (0x40): sent by the client immediately after the WebTransport
 * session opens, on the first bidi stream (the control stream).
 */
data class ClientSetup(
    val supportedVersions: List<Long>,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.ClientSetup

    init {
        require(supportedVersions.isNotEmpty()) { "must offer at least one version" }
    }
}

/**
 * SERVER_SETUP (0x41): response to CLIENT_SETUP. Contains the one version the
 * server selected from [ClientSetup.supportedVersions].
 */
data class ServerSetup(
    val selectedVersion: Long,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.ServerSetup
}
