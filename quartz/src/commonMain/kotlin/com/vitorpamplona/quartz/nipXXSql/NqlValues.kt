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
package com.vitorpamplona.quartz.nipXXSql

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.truncate

/**
 * NQL's value semantics (NIP-FF, Language reference): checked 64-bit
 * arithmetic, finite binary64 arithmetic, code-point text, `LIKE`, `CAST` and
 * the scalar functions. Values are `Long`, `Double`, `String`, `Boolean`;
 * callers handle NULL (Kotlin null) before calling in.
 */
internal object NqlValues {
    fun fail(what: String): Nothing = throw SqlException.error(what)

    private fun real(v: Any) = if (v is Long) v.toDouble() else v as Double

    // ---- Arithmetic -------------------------------------------------------

    fun add(
        a: Any,
        b: Any,
    ): Any {
        if (a is Long && b is Long) {
            val r = a + b
            if (((a xor r) and (b xor r)) < 0) fail("integer overflow")
            return r
        }
        return finite(real(a) + real(b))
    }

    fun subtract(
        a: Any,
        b: Any,
    ): Any {
        if (a is Long && b is Long) {
            val r = a - b
            if (((a xor b) and (a xor r)) < 0) fail("integer overflow")
            return r
        }
        return finite(real(a) - real(b))
    }

    fun multiply(
        a: Any,
        b: Any,
    ): Any {
        if (a is Long && b is Long) return multiplyExact(a, b)
        val x = real(a)
        val y = real(b)
        val r = finite(x * y)
        if (r == 0.0 && x != 0.0 && y != 0.0) fail("real underflow")
        return r
    }

    fun multiplyExact(
        a: Long,
        b: Long,
    ): Long {
        val r = a * b
        if (a != 0L && (r / a != b || (a == -1L && b == Long.MIN_VALUE))) fail("integer overflow")
        return r
    }

    fun divide(
        a: Any,
        b: Any,
    ): Any {
        if (a is Long && b is Long) {
            if (b == 0L) fail("division by zero")
            if (a == Long.MIN_VALUE && b == -1L) fail("integer overflow")
            return a / b
        }
        val x = real(a)
        val y = real(b)
        if (y == 0.0) fail("division by zero")
        val r = finite(x / y)
        if (r == 0.0 && x != 0.0) fail("real underflow")
        return r
    }

    fun remainder(
        a: Long,
        b: Long,
    ): Long {
        if (b == 0L) fail("division by zero")
        return a % b
    }

    fun negate(a: Any): Any {
        if (a is Long) {
            if (a == Long.MIN_VALUE) fail("integer overflow")
            return -a
        }
        return -(a as Double)
    }

    private fun finite(d: Double): Double {
        if (!d.isFinite()) fail("real overflow")
        return d
    }

    // ---- Comparison -------------------------------------------------------

    /** Orders two non-NULL values of comparable types: numbers by value, TEXT by code point, FALSE before TRUE. */
    fun compare(
        a: Any,
        b: Any,
    ): Int =
        when {
            a is Long && b is Long -> a.compareTo(b)
            a is String && b is String -> compareText(a, b)
            a is Boolean && b is Boolean -> a.compareTo(b)
            else -> {
                val x = real(a)
                val y = real(b)
                if (x < y) {
                    -1
                } else if (x > y) {
                    1
                } else {
                    0
                }
            }
        }

    fun equal(
        a: Any,
        b: Any,
    ): Boolean =
        when {
            a is Double || b is Double -> real(a) == real(b)
            else -> a == b
        }

    /** Code point order, which UTF-16 order is not: a surrogate sorts after U+E000..U+FFFF. */
    fun compareText(
        a: String,
        b: String,
    ): Int {
        val n = minOf(a.length, b.length)
        for (i in 0 until n) {
            val x = a[i]
            val y = b[i]
            if (x != y) {
                if (x >= '\uD800' && y >= '\uD800') return fixup(x) - fixup(y)
                return x - y
            }
        }
        return a.length - b.length
    }

    private fun fixup(c: Char): Int = if (c >= '') c.code - 0x800 else c.code + 0x2000

    /**
     * The value as a hash key: equal values give equal keys. A column's values
     * share one type; [asReal] turns numbers into `Double` where INTEGER meets REAL.
     */
    fun key(
        v: Any?,
        asReal: Boolean = false,
    ): Any? =
        when {
            v is Double -> if (v == 0.0) 0.0 else v
            asReal && v is Long -> v.toDouble().let { if (it == 0.0) 0.0 else it }
            else -> v
        }

    // ---- Text -------------------------------------------------------------

    /** Char offsets of each code point's start in [s], then `s.length`. */
    private fun offsets(s: String): IntArray {
        val out = IntArray(s.length + 1)
        var n = 0
        var i = 0
        while (i < s.length) {
            out[n++] = i
            i += if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
        }
        out[n++] = s.length
        return out.copyOf(n)
    }

    private fun codePoints(s: String): IntArray {
        val out = IntArray(s.length)
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                out[n++] = 0x10000 + ((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00)
                i += 2
            } else {
                out[n++] = c.code
                i++
            }
        }
        return out.copyOf(n)
    }

    fun length(s: String): Long = (offsets(s).size - 1).toLong()

    fun lower(s: String): String =
        CharArray(s.length) {
            val c = s[it]
            if (c in 'A'..'Z') c + 32 else c
        }.concatToString()

    fun upper(s: String): String =
        CharArray(s.length) {
            val c = s[it]
            if (c in 'a'..'z') c - 32 else c
        }.concatToString()

    fun trim(
        s: String,
        chars: String,
        start: Boolean,
        end: Boolean,
    ): String {
        val set = codePoints(chars).toHashSet()
        val cps = codePoints(s)
        val off = offsets(s)
        var from = 0
        var to = cps.size
        if (start) while (from < to && cps[from] in set) from++
        if (end) while (to > from && cps[to - 1] in set) to--
        return s.substring(off[from], off[to])
    }

    fun replace(
        s: String,
        from: String,
        to: String,
    ): String = if (from.isEmpty()) s else s.replace(from, to)

    fun instr(
        s: String,
        sub: String,
    ): Long {
        val i = s.indexOf(sub)
        if (i < 0) return 0
        return length(s.substring(0, i)) + 1
    }

    /** Code points at 1-based positions [start] .. [start] + [len] - 1 that exist; NULL when [len] is negative. */
    fun substr(
        s: String,
        start: Long,
        len: Long?,
    ): String? {
        if (len != null && len < 0) return null
        val off = offsets(s)
        val count = (off.size - 1).toLong()
        val first = maxOf(start, 1L)
        // start + len - 1, saturating.
        val last = if (len == null) count else minOf(count, if (start > 0 && len > Long.MAX_VALUE - start) Long.MAX_VALUE else start + len - 1)
        if (last < first || first > count) return ""
        return s.substring(off[(first - 1).toInt()], off[last.toInt()])
    }

    fun like(
        s: String,
        pattern: String,
    ): Boolean {
        val x = codePoints(s)
        val p = codePoints(pattern)
        var si = 0
        var pi = 0
        var star = -1
        var mark = 0
        while (si < x.size) {
            when {
                pi < p.size && p[pi] == '%'.code -> {
                    star = pi++
                    mark = si
                }

                pi < p.size && (p[pi] == '_'.code || p[pi] == x[si]) -> {
                    pi++
                    si++
                }

                star >= 0 -> {
                    pi = star + 1
                    si = ++mark
                }

                else -> {
                    return false
                }
            }
        }
        while (pi < p.size && p[pi] == '%'.code) pi++
        return pi == p.size
    }

    // ---- CAST -------------------------------------------------------------

    private val INTEGER_TEXT = Regex("[+-]?[0-9]+")
    private val REAL_TEXT = Regex("[+-]?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun cast(
        v: Any,
        target: NqlType,
    ): Any? =
        when (target) {
            NqlType.INTEGER -> {
                when (v) {
                    is Long -> v
                    is Boolean -> if (v) 1L else 0L
                    is Double -> if (v >= -9.223372036854775808E18 && v < 9.223372036854775808E18) v.toLong() else null
                    is String -> if (INTEGER_TEXT.matches(v)) v.removePrefix("+").toLongOrNull() else null
                    else -> null
                }
            }

            NqlType.REAL -> {
                when (v) {
                    is Long -> v.toDouble()
                    is Double -> v
                    is String -> textToReal(v)
                    else -> null
                }
            }

            NqlType.TEXT -> {
                when (v) {
                    is String -> v
                    is Long -> v.toString()
                    else -> null
                }
            }

            else -> {
                null
            }
        }

    private fun textToReal(s: String): Double? {
        if (!REAL_TEXT.matches(s)) return null
        val d = s.toDouble()
        if (!d.isFinite()) return null
        if (d == 0.0 && s.substringBefore('e').substringBefore('E').any { it in '1'..'9' }) return null
        return d
    }

    // ---- Numeric functions ------------------------------------------------

    fun abs(v: Any): Any =
        if (v is Long) {
            if (v == Long.MIN_VALUE) fail("integer overflow")
            if (v < 0) -v else v
        } else {
            kotlin.math.abs(v as Double)
        }

    fun math(
        name: String,
        v: Any,
    ): Double {
        val x = real(v)
        return when (name) {
            "round" -> {
                round(x)
            }

            "ceil" -> {
                ceil(x)
            }

            "floor" -> {
                floor(x)
            }

            "trunc" -> {
                truncate(x)
            }

            "sqrt" -> {
                if (x < 0) fail("sqrt of a negative number")
                sqrt(x)
            }

            "exp" -> {
                val r = finite(exp(x))
                if (r == 0.0) fail("real underflow")
                r
            }

            "ln" -> {
                if (x <= 0) fail("logarithm of zero or a negative number")
                ln(x)
            }

            "log10" -> {
                if (x <= 0) fail("logarithm of zero or a negative number")
                log10(x)
            }

            else -> {
                throw IllegalStateException(name)
            }
        }
    }

    fun pow(
        a: Any,
        b: Any,
    ): Double {
        val x = real(a)
        val y = real(b)
        if (x == 0.0 && y < 0) fail("zero raised to a negative power")
        if (x < 0 && y != floor(y)) fail("a negative number raised to a non-integer power")
        val r = finite(x.pow(y))
        if (r == 0.0 && x != 0.0) fail("real underflow")
        return r
    }
}

/** One aggregate's running state over a group. */
internal class NqlAggregateState(
    private val call: NqlCall,
) {
    private var count = 0L
    private var any = false
    private var hi = 0L
    private var lo = 0L
    private var real = 0.0
    private var best: Any? = null
    private val seen: HashSet<Any?>? = if (call.distinct) HashSet() else null

    /** Adds one row's argument value ([v] is ignored for `count(*)`). */
    fun add(v: Any?) {
        if (call.star) {
            count++
            return
        }
        if (v == null) return
        if (seen != null && !seen.add(NqlValues.key(v))) return
        count++
        any = true
        when (call.name) {
            "sum", "avg" -> {
                if (v is Long) {
                    // Exact, in 128 bits: only the final value must fit.
                    val sum = lo + v
                    val carry = if (sum.toULong() < lo.toULong()) 1L else 0L
                    hi += (if (v < 0) -1L else 0L) + carry
                    lo = sum
                } else {
                    real += v as Double
                }
            }

            "min" -> {
                if (best == null || NqlValues.compare(v, best!!) < 0) best = v
            }

            "max" -> {
                if (best == null || NqlValues.compare(v, best!!) > 0) best = v
            }
        }
    }

    fun result(): Any? =
        when (call.name) {
            "count" -> {
                count
            }

            "sum" -> {
                if (!any) {
                    null
                } else if (call.args[0].type == NqlType.REAL) {
                    if (!real.isFinite()) NqlValues.fail("real overflow") else real
                } else {
                    if (hi != (lo shr 63)) NqlValues.fail("integer overflow")
                    lo
                }
            }

            "avg" -> {
                if (!any) {
                    null
                } else if (call.args[0].type == NqlType.REAL) {
                    val r = real / count
                    if (!r.isFinite()) NqlValues.fail("real overflow") else r
                } else {
                    (hi.toDouble() * 18446744073709551616.0 + lo.toULong().toDouble()) / count
                }
            }

            else -> {
                best
            }
        }
}
