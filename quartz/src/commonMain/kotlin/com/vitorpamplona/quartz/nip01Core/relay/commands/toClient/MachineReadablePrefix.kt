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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

/**
 * Standardized machine-readable prefixes for the human-readable `message`
 * carried by `OK` and `CLOSED` frames.
 *
 * NIP-01 defines the convention that the message "SHOULD" begin with a single
 * word prefix followed by `: ` so clients can react programmatically — e.g.
 * `"blocked: you are banned from posting here"`. NIP-42 adds `auth-required`
 * and `restricted` for authentication gating.
 *
 * Use [format] to build a reason string and [parse] to read one back, instead
 * of hand-writing the prefixes.
 */
enum class MachineReadablePrefix(
    val code: String,
) {
    /** The event was already in the relay's store. */
    DUPLICATE("duplicate"),

    /** Proof-of-work (NIP-13) was missing or insufficient. */
    POW("pow"),

    /** The pubkey or event is blocked by the relay. */
    BLOCKED("blocked"),

    /** The client is being rate limited. */
    RATE_LIMITED("rate-limited"),

    /** The event is malformed or fails validation. */
    INVALID("invalid"),

    /** The action is not permitted for this client (often pre-auth). */
    RESTRICTED("restricted"),

    /** The client must authenticate (NIP-42) before this action is allowed. */
    AUTH_REQUIRED("auth-required"),

    /** A generic, usually transient, server-side failure. */
    ERROR("error"),
    ;

    /** Builds a reason string in the NIP-01 `"<code>: <message>"` form. */
    fun format(message: String): String = "$code: $message"

    companion object {
        private val byCode = entries.associateBy { it.code }

        /**
         * Extracts the standardized prefix from a reason string, or null when
         * the reason has no recognized machine-readable prefix.
         */
        fun parse(reason: String): MachineReadablePrefix? {
            val colon = reason.indexOf(':')
            if (colon <= 0) return null
            return byCode[reason.substring(0, colon)]
        }
    }
}
