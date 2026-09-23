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
 * One 85-bit axis value, split at 64 bits because no primitive holds it.
 *
 * [high] is bits 64 to 84, so at most 21 bits and always positive. [low] is bits 0
 * to 63 as a raw Long, which is to say unsigned: an axis near the top of its range
 * has its sign bit set and is not a negative number. Nothing here compares or adds
 * axes, so that distinction stays contained; the moment arithmetic is needed — the
 * Cantor trees of §4 — this becomes a big integer at the boundary and nowhere
 * earlier, the same rule the SNO lattice follows.
 */
class CyberspaceAxis(
    val high: Long,
    val low: Long,
) {
    /**
     * The sector this axis falls in: the axis shifted right by 30 (§10).
     *
     * 85 bits less 30 is 55, so a sector index is one of the few things about a
     * coordinate that does fit a Long, which is why the network writes them as
     * decimal `X`, `Y`, `Z` and `S` tag values and why they are worth extracting
     * when the axis itself is not.
     */
    fun sector(): Long = (high shl (Long.SIZE_BITS - CyberspaceCoordinate.SECTOR_SHIFT)) or (low ushr CyberspaceCoordinate.SECTOR_SHIFT)

    /**
     * This axis with its low [height] bits cleared: the base of the aligned subtree
     * of that height containing it (§4.5, `base = (v >> h) << h`).
     */
    fun alignedBase(height: Int): CyberspaceAxis {
        if (height <= 0) return this
        if (height >= CyberspaceCoordinate.AXIS_BITS) return CyberspaceAxis(0L, 0L)
        if (height >= Long.SIZE_BITS) {
            val keep = height - Long.SIZE_BITS
            return CyberspaceAxis((high shr keep) shl keep, 0L)
        }
        return CyberspaceAxis(high, (low ushr height) shl height)
    }

    override fun equals(other: Any?): Boolean = other is CyberspaceAxis && other.high == high && other.low == low

    override fun hashCode(): Int = high.hashCode() * 31 + low.hashCode()
}

/** A place: three axes and the plane they are in (§2.1). */
class CyberspacePoint(
    val x: CyberspaceAxis,
    val y: CyberspaceAxis,
    val z: CyberspaceAxis,
    val plane: CyberspacePlane,
) {
    /**
     * The `S` tag's value: the three sector indices joined by hyphens, in axis
     * order, exactly as §7.7's golden vectors write it.
     */
    fun sector(): String = "${x.sector()}-${y.sector()}-${z.sector()}"

    fun alignedBase(height: Int) = CyberspacePoint(x.alignedBase(height), y.alignedBase(height), z.alignedBase(height), plane)

    override fun equals(other: Any?): Boolean = other is CyberspacePoint && other.x == x && other.y == y && other.z == z && other.plane == plane

    override fun hashCode(): Int = ((x.hashCode() * 31 + y.hashCode()) * 31 + z.hashCode()) * 31 + plane.ordinal
}

/**
 * `CYBERSPACE_V2.md` §2: a place as a 256-bit integer, written as 32 lowercase
 * bytes of hex.
 *
 * The three 85-bit axes are **interleaved** rather than packed, `XYZXYZ…` from the
 * top with the plane bit at the bottom (§2.2), so that coordinates which are close
 * in space share a bit prefix — which is what makes an aligned Cantor subtree (§4.5)
 * a region everyone agrees on without communicating, and therefore what makes §7's
 * location-based keys work at all.
 *
 * Bit 0 is the plane. Bits `1, 4, 7, …` are Z, bits `2, 5, 8, …` are Y and bits
 * `3, 6, 9, …` are X, each 85 of them, which is `85 × 3 + 1 = 256` exactly.
 *
 * An item hidden at a place carries one of these in a `C` tag (§7.6); a hint names
 * an aligned box with one (§7.7).
 */
object CyberspaceCoordinate {
    /** 32 bytes, as §7.6 and §8 write it. */
    const val HEX_LENGTH = 64

    /** Bits per axis (§2.1). */
    const val AXIS_BITS = 85

    /** A sector is an axis shifted right by this (§10). */
    const val SECTOR_SHIFT = 30

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
     * Bit 0 of the 256-bit integer, which is the low bit of the last hex digit.
     * Kept separate from [decode] because it is the one part of a coordinate a
     * client with no world can use on its own, and it costs a character.
     */
    fun planeOf(hex: String): CyberspacePlane? {
        if (!isWellFormed(hex)) return null
        return if (nibble(hex, HEX_LENGTH - 1) and 1 == 1) CyberspacePlane.IDEASPACE else CyberspacePlane.DATASPACE
    }

    /** The three axes and the plane, or null when the string is not a coordinate. */
    fun decode(hex: String): CyberspacePoint? {
        if (!isWellFormed(hex)) return null

        var xHigh = 0L
        var xLow = 0L
        var yHigh = 0L
        var yLow = 0L
        var zHigh = 0L
        var zLow = 0L

        for (i in 0 until AXIS_BITS) {
            val z = bit(hex, 1 + i * 3)
            val y = bit(hex, 2 + i * 3)
            val x = bit(hex, 3 + i * 3)
            if (i < Long.SIZE_BITS) {
                xLow = xLow or (x shl i)
                yLow = yLow or (y shl i)
                zLow = zLow or (z shl i)
            } else {
                val at = i - Long.SIZE_BITS
                xHigh = xHigh or (x shl at)
                yHigh = yHigh or (y shl at)
                zHigh = zHigh or (z shl at)
            }
        }

        return CyberspacePoint(
            CyberspaceAxis(xHigh, xLow),
            CyberspaceAxis(yHigh, yLow),
            CyberspaceAxis(zHigh, zLow),
            planeOf(hex)!!,
        )
    }

    /** The coordinate a point writes as, the inverse of [decode]. */
    fun encode(point: CyberspacePoint): String {
        val nibbles = IntArray(HEX_LENGTH)
        if (point.plane == CyberspacePlane.IDEASPACE) nibbles[HEX_LENGTH - 1] = 1

        for (i in 0 until AXIS_BITS) {
            setBit(nibbles, 1 + i * 3, axisBit(point.z, i))
            setBit(nibbles, 2 + i * 3, axisBit(point.y, i))
            setBit(nibbles, 3 + i * 3, axisBit(point.x, i))
        }

        val out = StringBuilder(HEX_LENGTH)
        for (n in nibbles) out.append(HEX[n])
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"

    /** Bit [i] of an axis, as 0 or 1. */
    private fun axisBit(
        axis: CyberspaceAxis,
        i: Int,
    ): Long =
        if (i < Long.SIZE_BITS) {
            (axis.low ushr i) and 1L
        } else {
            (axis.high ushr (i - Long.SIZE_BITS)) and 1L
        }

    /**
     * Bit [n] of the 256-bit integer the hex spells, counting from the least
     * significant. The string is big-endian, so bit `n` lives in the nibble `n / 4`
     * places from the end, at position `n % 4` inside it.
     */
    private fun bit(
        hex: String,
        n: Int,
    ): Long = ((nibble(hex, HEX_LENGTH - 1 - n / 4) ushr (n % 4)) and 1).toLong()

    private fun setBit(
        nibbles: IntArray,
        n: Int,
        value: Long,
    ) {
        if (value == 0L) return
        val at = HEX_LENGTH - 1 - n / 4
        nibbles[at] = nibbles[at] or (1 shl (n % 4))
    }

    private fun nibble(
        hex: String,
        at: Int,
    ): Int {
        val c = hex[at]
        return if (c in '0'..'9') c - '0' else c - 'a' + 10
    }
}
