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
package com.vitorpamplona.quartz.concord.cord04Roles

import kotlin.jvm.JvmInline

/**
 * A member's Concord permission set (CORD-04): a `u64` bitfield of frozen bit
 * positions. On the wire it is encoded as a **decimal string** (not a JSON
 * number) to prevent floating-point corruption of the high bits.
 *
 * Bit positions are frozen forever — new permissions claim the next free bit,
 * retired ones are burned and never reused. Bit 7 is retired; bits 10–12 are
 * reserved.
 *
 * "A member's effective permissions are the union of their Roles' bits."
 */
@JvmInline
value class ConcordPermissions(
    val bits: ULong,
) {
    fun has(bit: Int): Boolean = (bits and (1uL shl bit)) != 0uL

    /** True if every bit set in [other] is also set here (used for role-vs-actor checks). */
    fun hasAll(other: ConcordPermissions): Boolean = (bits and other.bits) == other.bits

    infix fun union(other: ConcordPermissions): ConcordPermissions = ConcordPermissions(bits or other.bits)

    fun with(bit: Int): ConcordPermissions = ConcordPermissions(bits or (1uL shl bit))

    fun without(bit: Int): ConcordPermissions = ConcordPermissions(bits and (1uL shl bit).inv())

    /** The wire encoding: an unsigned decimal string with no leading zeros. */
    fun toWire(): String = bits.toString()

    companion object {
        val NONE = ConcordPermissions(0uL)

        /** Every bit set — the owner's implicit, supreme permission set. */
        val ALL = ConcordPermissions(ULong.MAX_VALUE)

        // Frozen bit positions (CORD-04 §Permission Bits).
        const val MANAGE_ROLES = 0
        const val MANAGE_CHANNELS = 1
        const val MANAGE_METADATA = 2
        const val KICK = 3
        const val BAN = 4
        const val MANAGE_MESSAGES = 5
        const val CREATE_INVITE = 6

        // bit 7 retired — never reuse

        const val VIEW_AUDIT_LOG = 8
        const val MENTION_EVERYONE = 9

        // bits 10-12 reserved

        fun of(vararg bits: Int): ConcordPermissions {
            var acc = 0uL
            for (b in bits) acc = acc or (1uL shl b)
            return ConcordPermissions(acc)
        }

        /**
         * Parses the wire form — a decimal `u64` string. Returns [NONE] for a
         * blank value; throws [NumberFormatException] for a malformed or
         * out-of-range one so a corrupt edition is rejected rather than folded.
         */
        fun fromWire(wire: String): ConcordPermissions {
            if (wire.isBlank()) return NONE
            return ConcordPermissions(wire.trim().toULong())
        }

        /** Non-throwing variant: returns null on a malformed value. */
        fun fromWireOrNull(wire: String): ConcordPermissions? =
            try {
                fromWire(wire)
            } catch (_: NumberFormatException) {
                null
            }
    }
}
