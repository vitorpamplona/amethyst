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
package com.vitorpamplona.quartz.cordn.tlv

/**
 * A strict TLV parse for cordn's bech32 payloads.
 *
 * `Tlv.parse` in quartz stops silently at a malformed tuple, which is right for
 * NIP-19 — a truncated `nprofile` still names a usable pubkey, and half an
 * answer beats none. It is wrong for cordn's payloads, where a dropped tail
 * changes where the reader goes: a group ref quietly losing its coordinator
 * reaches for a default instead, and a handoff code quietly losing its relays
 * looks for the tip in the wrong place.
 *
 * Unknown TYPES are still ignored. That is forward compatibility, and a
 * different thing from a truncated payload.
 */
object CordnStrictTlv {
    /**
     * Parses [data], or throws naming [subject] and the rule it broke.
     *
     * @param subject what the caller is decoding, for the error message —
     * "cordn group ref", "handoff code".
     */
    fun parse(
        data: ByteArray,
        subject: String,
    ): Map<Byte, List<ByteArray>> {
        val result = mutableMapOf<Byte, MutableList<ByteArray>>()
        var pos = 0
        while (pos < data.size) {
            require(pos + 2 <= data.size) { "$subject has a truncated TLV header" }
            val type = data[pos]
            val length = data[pos + 1].toUByte().toInt()
            require(pos + 2 + length <= data.size) {
                "$subject TLV type $type declares $length bytes but only ${data.size - pos - 2} remain"
            }
            result.getOrPut(type) { mutableListOf() }.add(data.copyOfRange(pos + 2, pos + 2 + length))
            pos += 2 + length
        }
        return result
    }

    /** Decodes UTF-8, or throws naming [subject] and [field]. */
    fun utf8(
        bytes: ByteArray,
        subject: String,
        field: String,
    ): String =
        try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("$subject $field is not valid UTF-8", e)
        }
}
