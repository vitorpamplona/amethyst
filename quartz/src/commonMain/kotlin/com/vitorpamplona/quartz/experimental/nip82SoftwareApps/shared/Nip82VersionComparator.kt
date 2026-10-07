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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared

/**
 * NIP-82 Appendix D version ordering, for `version`, `min_platform_version`,
 * `min_allowed_version` and the like.
 *
 * A version is split on `.`, `-` and `_`, and a number glued to a word (`10rc1`) is split
 * into the two (a leading `v` and any `+build` suffix are ignored). The leading numeric segments are its release core, compared as integers with
 * a missing segment lower than a present one (`1.0 < 1.0.0 < 1.0.0.1`). Anything after
 * the core makes it a pre-release of that core, so it sorts below the bare core. The first
 * pre-release segment is ranked by its identifier (`dev` < `alpha` < `beta` < `rc`, then
 * unknown words alphabetically) and its trailing number (`rc1` < `rc2`); later segments
 * follow the generic rules: numbers as integers, words case-insensitively, numbers before
 * words, missing before present. A SemVer version (`MAJOR.MINOR.PATCH[-pre]`) takes its
 * pre-release from the first `-` on, numbers included, as SemVer precedence requires. This orders the spec's example as written:
 * `1.0.0-dev < 1.0.0-alpha < 1.0.0-alpha.2 < 1.0.0-beta < 1.0.0-beta.2 < 1.0.0-rc1 < 1.0.0 < 1.0.1`,
 * and agrees with SemVer precedence wherever the two do not conflict.
 */
object Nip82VersionComparator : Comparator<String> {
    private val SEPARATORS = charArrayOf('.', '-', '_')
    private val SEMVER = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?""")

    private class Parsed(
        val core: List<String>,
        val preRelease: List<String>,
    )

    private fun parse(version: String): Parsed {
        var v = version.trim().substringBefore('+')
        if (v.length > 1 && (v[0] == 'v' || v[0] == 'V') && v[1].isDigit()) v = v.substring(1)

        // SemVer, which Appendix D makes binding where it applies: the pre-release is everything
        // after the first `-`, so `1.0.0-1` is a pre-release of 1.0.0, not version 1.0.0.1.
        SEMVER.matchEntire(v)?.let { m ->
            val (major, minor, patch, pre) = m.destructured
            return Parsed(listOf(major, minor, patch), if (pre.isEmpty()) emptyList() else pre.split(*SEPARATORS))
        }

        val segments = v.split(*SEPARATORS).flatMap(::splitNumericPrefix)
        val coreSize = segments.indexOfFirst { !it.isNumeric() }.let { if (it < 0) segments.size else it }
        return Parsed(segments.subList(0, coreSize), segments.subList(coreSize, segments.size))
    }

    /**
     * `10rc1` is release number 10 followed by the pre-release `rc1`, as in `1.2.10rc1` or
     * Python's `1.0.0b2`; keeping it whole would drop the 10 from the release core.
     */
    private fun splitNumericPrefix(segment: String): List<String> {
        if (segment.isEmpty()) return emptyList()
        val digits = segment.indexOfFirst { it !in '0'..'9' }
        return if (digits <= 0) listOf(segment) else listOf(segment.substring(0, digits), segment.substring(digits))
    }

    override fun compare(
        a: String,
        b: String,
    ): Int {
        val pa = parse(a)
        val pb = parse(b)

        val core = compareSegments(pa.core, pb.core)
        if (core != 0) return core

        // Same core: a pre-release sorts below the release itself.
        if (pa.preRelease.isEmpty() || pb.preRelease.isEmpty()) {
            return pb.preRelease.size.coerceAtMost(1) - pa.preRelease.size.coerceAtMost(1)
        }

        val marker = compareMarker(pa.preRelease[0], pb.preRelease[0])
        if (marker != 0) return marker

        return compareSegments(pa.preRelease.drop(1), pb.preRelease.drop(1))
    }

    private fun compareSegments(
        a: List<String>,
        b: List<String>,
    ): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val sa = a.getOrNull(i) ?: return -1
            val sb = b.getOrNull(i) ?: return 1
            val c = compareSegment(sa, sb)
            if (c != 0) return c
        }
        return 0
    }

    private fun compareSegment(
        a: String,
        b: String,
    ): Int {
        val aNum = a.isNumeric()
        val bNum = b.isNumeric()
        return when {
            aNum && bNum -> compareNumeric(a, b)
            aNum -> -1
            bNum -> 1
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    /** `rc1` vs `beta2`: identifier rank first, then the trailing number (missing < present). */
    private fun compareMarker(
        a: String,
        b: String,
    ): Int {
        // A bare number (SemVer's `1.0.0-1`) is no identifier: numbers sort before words.
        if (a.isNumeric() || b.isNumeric()) return compareSegment(a, b)

        val aWord = a.trimEnd { it.isDigit() }
        val bWord = b.trimEnd { it.isDigit() }

        val rank = preReleaseRank(aWord).compareTo(preReleaseRank(bWord))
        if (rank != 0) return rank
        val word = aWord.compareTo(bWord, ignoreCase = true)
        if (word != 0) return word

        val aNum = a.substring(aWord.length)
        val bNum = b.substring(bWord.length)
        return when {
            aNum.isEmpty() && bNum.isEmpty() -> 0
            aNum.isEmpty() -> -1
            bNum.isEmpty() -> 1
            else -> compareNumeric(aNum, bNum)
        }
    }

    private fun preReleaseRank(word: String) =
        when (word.lowercase()) {
            "dev", "develop" -> 0
            "alpha", "a" -> 1
            "beta", "b" -> 2
            "rc", "pre", "preview" -> 3
            else -> 4
        }

    /** Integer comparison of digit strings of any length, without overflow. */
    private fun compareNumeric(
        a: String,
        b: String,
    ): Int {
        val ta = a.trimStart('0')
        val tb = b.trimStart('0')
        if (ta.length != tb.length) return ta.length.compareTo(tb.length)
        return ta.compareTo(tb)
    }

    private fun String.isNumeric() = isNotEmpty() && all { it in '0'..'9' }
}
