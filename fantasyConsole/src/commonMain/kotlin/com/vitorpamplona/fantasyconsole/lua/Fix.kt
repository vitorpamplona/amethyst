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
package com.vitorpamplona.fantasyconsole.lua

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 16:16 fixed-point numbers, the only number type of the console's Lua. A number is its raw
 * 32-bit pattern held in an [Int]: 16 integer bits (two's complement) and 16 fraction bits, so
 * its range is -32768.0 to 32767.99998. Addition and subtraction are plain [Int] arithmetic and
 * wrap the way the console does; everything else goes through here.
 */
object Fix {
    const val ONE = 0x10000
    const val MAX = 0x7fffffff
    const val MIN_PLUS_ONE = -0x7fffffff // 0x8000.0001, what a negative division by zero gives

    fun fromInt(i: Int): Int = i shl 16

    /** The integer part, rounded towards negative infinity. */
    fun toInt(f: Int): Int = f shr 16

    fun toDouble(f: Int): Double = f / 65536.0

    /** Rounds [d] to the nearest representable number, wrapping like the console on overflow. */
    fun fromDouble(d: Double): Int {
        if (d.isNaN()) return 0
        val scaled = d * 65536.0
        if (scaled >= 9.0e18) return MAX
        if (scaled <= -9.0e18) return Int.MIN_VALUE
        return scaled.roundToLong().toInt()
    }

    fun mul(
        a: Int,
        b: Int,
    ): Int = ((a.toLong() * b.toLong()) shr 16).toInt()

    fun div(
        a: Int,
        b: Int,
    ): Int {
        // Dividing by one must not round 0x8000 into 0x8000.0001.
        if (b == ONE) return a
        if (b != 0) {
            val result = (a.toLong() shl 16) / b
            if (abs(result) <= 0x7fffffffL) return result.toInt()
        }
        return if ((a xor b) >= 0) MAX else MIN_PLUS_ONE
    }

    /** The console's modulo is never negative: it lands in `[0, |b|)`. */
    fun mod(
        a: Int,
        b: Int,
    ): Int {
        if (b == 0) return 0
        val r = a % b
        return if (r >= 0) r else r + abs(b)
    }

    fun idiv(
        a: Int,
        b: Int,
    ): Int = flr(div(a, b))

    fun pow(
        a: Int,
        b: Int,
    ): Int = fromDouble(toDouble(a).pow(toDouble(b)))

    fun flr(f: Int): Int = f and -0x10000

    fun ceil(f: Int): Int = -flr(-f)

    fun shl(
        a: Int,
        n: Int,
    ): Int {
        val s = toInt(n)
        return when {
            s >= 32 -> 0
            s >= 0 -> a shl s
            s <= -32 -> a shr 31
            else -> a shr -s
        }
    }

    fun shr(
        a: Int,
        n: Int,
    ): Int {
        val s = toInt(n)
        return when {
            s >= 32 -> a shr 31
            s >= 0 -> a shr s
            s <= -32 -> 0
            else -> a shl -s
        }
    }

    fun lshr(
        a: Int,
        n: Int,
    ): Int {
        val s = toInt(n)
        return when {
            s >= 32 -> 0
            s >= 0 -> a ushr s
            s <= -32 -> 0
            else -> a shl -s
        }
    }

    fun rotl(
        a: Int,
        n: Int,
    ): Int = a.rotateLeft(toInt(n) and 31)

    fun rotr(
        a: Int,
        n: Int,
    ): Int = a.rotateRight(toInt(n) and 31)

    /** Up to four decimals, rounded, with trailing zeros dropped: `0.3333`, `-1.5`, `32767`. */
    fun toStr(f: Int): String {
        val negative = f < 0
        val magnitude = abs(f.toLong())
        val scaled = (magnitude * 10000 + 0x8000) shr 16
        val integer = scaled / 10000
        val fraction = (scaled % 10000).toInt()
        val sb = StringBuilder()
        if (negative && scaled != 0L) sb.append('-')
        sb.append(integer)
        if (fraction != 0) {
            sb.append('.')
            val digits = fraction.toString().padStart(4, '0').trimEnd('0')
            sb.append(digits)
        }
        return sb.toString()
    }

    /** `0x0000.0000`, the console's hex form. */
    fun toHexStr(f: Int): String = "0x" + hex4(f ushr 16) + "." + hex4(f and 0xffff)

    private fun hex4(v: Int): String = v.toString(16).padStart(4, '0')

    /**
     * Parses a number literal or a `tonum` string: decimal (`1.5`, `.5`; there is no exponent notation, so `1end` lexes as `1 end`), hex
     * (`0x1f.8`) or binary (`0b101.1`), with an optional leading minus. Integer parts past 32767
     * wrap like the console. Null when [s] is not a number.
     */
    fun parse(s: String): Int? {
        if (s.isEmpty()) return null
        var i = 0
        var negative = false
        if (s[0] == '-') {
            negative = true
            i = 1
        } else if (s[0] == '+') {
            i = 1
        }
        if (i >= s.length) return null
        val prefix = if (s.length > i + 1 && s[i] == '0') s[i + 1].lowercaseChar() else ' '
        val value =
            when (prefix) {
                'x' -> parseRadix(s, i + 2, 16)
                'b' -> parseRadix(s, i + 2, 2)
                else -> parseDecimal(s, i)
            } ?: return null
        return if (negative) -value else value
    }

    private fun digitValue(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> 99
        }

    private fun parseRadix(
        s: String,
        start: Int,
        radix: Int,
    ): Int? {
        var i = start
        var integer = 0L
        var any = false
        while (i < s.length && digitValue(s[i]) < radix) {
            integer = (integer * radix + digitValue(s[i])) and 0xffffffffL
            i++
            any = true
        }
        var fraction = 0L
        if (i < s.length && s[i] == '.') {
            i++
            // Each digit is worth 1/radix of the one before; the first 16 bits of it are kept.
            var weight = ONE.toLong()
            while (i < s.length && digitValue(s[i]) < radix) {
                weight /= radix
                fraction += digitValue(s[i]) * weight
                i++
                any = true
            }
        }
        if (!any || i != s.length) return null
        return ((integer shl 16) + fraction).toInt()
    }

    private fun parseDecimal(
        s: String,
        start: Int,
    ): Int? {
        var i = start
        var mantissa = 0.0
        var any = false
        while (i < s.length && s[i] in '0'..'9') {
            mantissa = mantissa * 10 + (s[i] - '0')
            i++
            any = true
        }
        if (i < s.length && s[i] == '.') {
            i++
            var weight = 0.1
            while (i < s.length && s[i] in '0'..'9') {
                mantissa += (s[i] - '0') * weight
                weight /= 10
                i++
                any = true
            }
        }
        if (!any) return null
        if (i != s.length) return null
        // Integer parts wrap at 16 bits, as the console's do: 65535 reads as -1.
        val whole = floor(mantissa)
        val wrapped = (whole.toLong() and 0xffff).toDouble() + (mantissa - whole)
        return fromDouble(wrapped)
    }
}
