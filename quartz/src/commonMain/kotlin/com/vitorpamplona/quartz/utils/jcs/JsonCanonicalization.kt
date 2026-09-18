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
package com.vitorpamplona.quartz.utils.jcs

/**
 * RFC 8785 JSON Canonicalization Scheme (JCS).
 *
 * Produces the one serialization of a JSON value that every conformant
 * implementation agrees on, so a hash over the result is portable. Used wherever
 * a protocol hashes structured data rather than bytes it was handed — ContextVM
 * needs it twice (CEP-8's canonical invocation identity and CEP-15's schema
 * hash), and it is generic enough to belong here rather than in that module.
 *
 * The rules:
 *  - no insignificant whitespace
 *  - object members sorted by key, compared as UTF-16 code units
 *  - strings escaped minimally, with non-ASCII left literal (output is UTF-8)
 *  - numbers serialized exactly as ECMAScript `Number.prototype.toString()`
 *
 * The number rule is the subtle one and [canonicalNumber] implements it in full.
 */
object JsonCanonicalization {
    /**
     * Serializes [value] canonically.
     *
     * [value] is a plain JSON tree: `Map<String, Any?>`, `List<Any?>`, [String],
     * [Boolean], a number, or null. Using plain types rather than a JSON library's
     * node types keeps this usable from any module and any serializer.
     */
    fun canonicalize(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(
        value: Any?,
        out: StringBuilder,
    ) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is String -> writeString(value, out)
            is Map<*, *> -> writeObject(value, out)
            is List<*> -> writeArray(value, out)
            is Double -> out.append(canonicalNumber(value))
            is Float -> out.append(canonicalNumber(value.toDouble()))
            is Int -> out.append(canonicalNumber(value.toDouble()))
            is Long -> out.append(canonicalNumber(value.toDouble()))
            is Short -> out.append(canonicalNumber(value.toDouble()))
            is Byte -> out.append(canonicalNumber(value.toDouble()))
            else -> throw IllegalArgumentException("cannot canonicalize ${value::class.simpleName}")
        }
    }

    private fun writeObject(
        value: Map<*, *>,
        out: StringBuilder,
    ) {
        out.append('{')
        value.entries
            .map { (key, entry) ->
                (key as? String ?: throw IllegalArgumentException("object keys must be strings")) to entry
            }
            // RFC 8785 sorts by UTF-16 code unit, which is exactly what Kotlin's
            // natural String ordering does. Do not swap this for a locale-aware
            // or codepoint-aware comparison.
            .sortedBy { it.first }
            .forEachIndexed { index, (key, entry) ->
                if (index > 0) out.append(',')
                writeString(key, out)
                out.append(':')
                write(entry, out)
            }
        out.append('}')
    }

    private fun writeArray(
        value: List<*>,
        out: StringBuilder,
    ) {
        out.append('[')
        value.forEachIndexed { index, entry ->
            if (index > 0) out.append(',')
            write(entry, out)
        }
        out.append(']')
    }

    private fun writeString(
        value: String,
        out: StringBuilder,
    ) {
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else ->
                    if (char < '\u0020') {
                        // Only C0 controls without a short escape use \u, and the
                        // hex digits are lowercase.
                        out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        // Everything else stays literal, non-ASCII included: the
                        // canonical form is UTF-8, not \u-escaped ASCII.
                        out.append(char)
                    }
            }
        }
        out.append('"')
    }

    /**
     * Serializes [value] as ECMAScript `Number.prototype.toString()` does, which
     * is what RFC 8785 requires and is *not* what any JVM/Kotlin `toString()`
     * produces (`1.0E30` where ECMAScript says `1e+30`).
     *
     * The digits come from the platform's [Double.toString], but they are then
     * shortened explicitly until the shortest form that still round-trips is
     * found. That extra step is not optional: JVM prints [Double.MIN_VALUE] as
     * `4.9E-324` while ECMAScript requires `5e-324`, so trusting the platform to
     * already be shortest produces a different hash from every other conformant
     * implementation. Shortening here makes the result platform-independent.
     */
    fun canonicalNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) {
            throw IllegalArgumentException("JCS cannot represent $value")
        }
        // ECMAScript prints both zeroes as "0"; JCS inherits that, so -0.0 and
        // 0.0 canonicalize identically.
        if (value == 0.0) return "0"
        if (value < 0) return "-" + canonicalNumber(-value)

        val raw = value.toString()
        val exponentSplit = raw.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (exponentSplit >= 0) raw.substring(0, exponentSplit) else raw
        val exponent = if (exponentSplit >= 0) raw.substring(exponentSplit + 1).toInt() else 0

        val pointIndex = mantissa.indexOf('.')
        val digits = if (pointIndex >= 0) mantissa.removeRange(pointIndex, pointIndex + 1) else mantissa
        val fractionLength = if (pointIndex >= 0) mantissa.length - pointIndex - 1 else 0

        // `s` is the shortest digit string, `n` its decimal exponent, such that
        // value = s * 10^(n - k) with k = s.length. This is ECMAScript's (s, n, k).
        val trailingZeros = digits.length - digits.trimEnd('0').length
        val initial = digits.trim('0').ifEmpty { "0" }
        val (significant, n) =
            shorten(value, initial, initial.length + exponent - fractionLength + trailingZeros)
        val k = significant.length

        return when {
            // Integral, short enough to print plainly.
            n in k..21 -> significant + "0".repeat(n - k)
            // Has a fractional part but no exponent needed.
            n in 1..21 -> significant.substring(0, n) + "." + significant.substring(n)
            // Small enough for a leading "0." but not for an exponent.
            n in -5..0 -> "0." + "0".repeat(-n) + significant
            // Exponential form.
            k == 1 -> significant + "e" + exponentSuffix(n - 1)
            else -> significant.substring(0, 1) + "." + significant.substring(1) + "e" + exponentSuffix(n - 1)
        }
    }

    /**
     * Finds the shortest digit string that still parses back to [value].
     *
     * ECMAScript defines the digits as the fewest that round-trip, so a platform
     * that prints more (JVM does, for some subnormals) has to be corrected here
     * or the canonical form diverges.
     */
    private fun shorten(
        value: Double,
        digits: String,
        exponent: Int,
    ): Pair<String, Int> {
        for (length in 1 until digits.length) {
            val (candidate, candidateExponent) = roundTo(digits, exponent, length)
            val rebuilt = "${candidate}e${candidateExponent - candidate.length}".toDouble()
            if (rebuilt == value) return candidate to candidateExponent
        }
        return digits to exponent
    }

    /** Rounds [digits] to [length] significant digits, half-up, carrying into [exponent]. */
    private fun roundTo(
        digits: String,
        exponent: Int,
        length: Int,
    ): Pair<String, Int> {
        val kept = digits.substring(0, length)
        if (digits[length] < '5') return kept to exponent

        val incremented = (kept.toLong() + 1).toString()
        // "99" + 1 becomes "100": one digit longer, so drop the last and shift
        // the exponent rather than growing the significand.
        return if (incremented.length > length) {
            incremented.substring(0, length) to exponent + 1
        } else {
            incremented.padStart(length, '0') to exponent
        }
    }

    private fun exponentSuffix(exponent: Int) = if (exponent >= 0) "+$exponent" else "-${-exponent}"
}
