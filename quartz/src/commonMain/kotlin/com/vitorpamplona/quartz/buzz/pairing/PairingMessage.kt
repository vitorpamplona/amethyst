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
package com.vitorpamplona.quartz.buzz.pairing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A NIP-AB (Buzz device-pairing, `kind:24134`) message: the plaintext that is NIP-44 v2
 * encrypted into a [PairingEvent] `content`. The JSON carries a `"type"` discriminant in
 * kebab-case (`offer`, `sas-confirm`, `payload`, `complete`, `abort`); the inner fields are
 * snake_case. Ground truth: `buzz-core/src/pairing/types.rs::PairingMessage`.
 */
@Serializable
sealed class PairingMessage {
    /** Target -> Source. Announces the session and proves possession of the QR secret. */
    @Serializable
    @SerialName("offer")
    data class Offer(
        @SerialName("session_id") val sessionId: String,
        val version: Int = 1,
    ) : PairingMessage()

    /** Either party -> other. Confirms the Short Authentication String matches. */
    @Serializable
    @SerialName("sas-confirm")
    data class SasConfirm(
        @SerialName("transcript_hash") val transcriptHash: String,
    ) : PairingMessage()

    /** Delivers the actual secret payload. */
    @Serializable
    @SerialName("payload")
    data class Payload(
        @SerialName("payload_type") val payloadType: PayloadType,
        val payload: String,
    ) : PairingMessage()

    /** Signals successful (or partially failed) session completion. */
    @Serializable
    @SerialName("complete")
    data class Complete(
        val success: Boolean,
    ) : PairingMessage()

    /**
     * Aborts the session early. `reason` is kept as the raw wire string so an unrecognized
     * value (a future/extended implementation) round-trips losslessly; map it with
     * [reasonOrUnknown], which treats unknown reasons as [AbortReason.UNKNOWN] per NIP-AB.
     */
    @Serializable
    @SerialName("abort")
    data class Abort(
        val reason: String,
    ) : PairingMessage() {
        constructor(reason: AbortReason) : this(reason.code)

        fun reasonOrUnknown(): AbortReason = AbortReason.fromWire(reason)
    }

    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
                classDiscriminator = "type"
            }

        fun decodeFromJson(json: String): PairingMessage = JSON.decodeFromString(json)
    }
}

/** Discriminates the content of a [PairingMessage.Payload] message (snake_case wire values). */
@Serializable
enum class PayloadType {
    /** Raw `nsec` bech32 secret key. */
    @SerialName("nsec")
    NSEC,

    /** NIP-46 bunker connection string. */
    @SerialName("bunker")
    BUNKER,

    /** NIP-46 `nostrconnect://` URI. */
    @SerialName("connect")
    CONNECT,

    /** Application-defined payload; interpretation is out-of-band. */
    @SerialName("custom")
    CUSTOM,
}

/** Machine-readable reason a pairing session was aborted (snake_case wire values). */
enum class AbortReason(
    val code: String,
) {
    SAS_MISMATCH("sas_mismatch"),
    USER_DENIED("user_denied"),
    TIMEOUT("timeout"),
    PROTOCOL_ERROR("protocol_error"),

    /** An unrecognized reason from a future/extended implementation. Treat as `protocol_error`. */
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String): AbortReason =
            when (value) {
                SAS_MISMATCH.code -> SAS_MISMATCH
                USER_DENIED.code -> USER_DENIED
                TIMEOUT.code -> TIMEOUT
                PROTOCOL_ERROR.code -> PROTOCOL_ERROR
                else -> UNKNOWN
            }
    }
}
