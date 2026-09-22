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
package com.vitorpamplona.quartz.cyberspace

/** Which of the two overlapping coordinate spaces a place is in (§2.4). */
enum class CyberspacePlane {
    /** Maps to physical reality through GPS (§9). */
    DATASPACE,

    /** No physical counterpart; purely abstract positions. */
    IDEASPACE,
}

/**
 * The little of `CYBERSPACE_V2.md` §2 a client without a world can use.
 *
 * A coordinate is a 256-bit integer, written as 32 lowercase bytes of hex: the
 * three 85-bit axes interleaved `XYZXYZ…` with the plane bit in the least
 * significant position (§2.2). An item hidden at a place carries one in a `C`
 * tag (§7.6).
 *
 * Only the plane bit is read here, and that is a deliberate stopping point
 * rather than a half-finished decoder. Pulling X, Y and Z back out is the
 * twenty lines of §2.3 plus an 85-bit decimal printer, and what it would put on
 * screen is three twenty-six-digit integers, which tell a reader with no world
 * to place them in exactly nothing. The reading that *would* mean something —
 * the GPS position under a dataspace coordinate — is §9.7, and that is 96-digit
 * decimal arithmetic with a hand-rolled deterministic trig series, carried so
 * that every client agrees on where a place on Earth is. Amethyst has no Earth
 * to agree about. So it reports the plane, which is one bit and says whether
 * the thing was hidden somewhere real, and hands the coordinate itself over
 * intact for a client that does.
 */
object CyberspaceCoordinate {
    /** 32 bytes, as §7.6 and §8 write it. */
    const val HEX_LENGTH = 64

    /** True when this is a coordinate at all: 64 lowercase hex characters. */
    fun isWellFormed(hex: String): Boolean {
        if (hex.length != HEX_LENGTH) return false
        for (c in hex) {
            val ok = (c in '0'..'9') || (c in 'a'..'f')
            if (!ok) return false
        }
        return true
    }

    /**
     * The plane a coordinate names, or null when the string is not one.
     *
     * Bit 0 of the 256-bit integer, which is the low bit of the last hex digit
     * (§2.2: "Bit `0` (LSB): plane bit `P`").
     */
    fun planeOf(hex: String): CyberspacePlane? {
        if (!isWellFormed(hex)) return null
        val last = hex[HEX_LENGTH - 1]
        val nibble = if (last in '0'..'9') last - '0' else last - 'a' + 10
        return if (nibble and 1 == 1) CyberspacePlane.IDEASPACE else CyberspacePlane.DATASPACE
    }
}
