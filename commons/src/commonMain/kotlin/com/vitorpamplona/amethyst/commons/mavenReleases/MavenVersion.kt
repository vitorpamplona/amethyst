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
package com.vitorpamplona.amethyst.commons.mavenReleases

/**
 * Maven's version order, as Gradle and Maven's `ComparableVersion` sort versions, close enough to
 * tell which of a library's releases is newer: `1.0-alpha1 < 1.0-beta < 1.0-rc1 < 1.0-SNAPSHOT <
 * 1.0 = 1.0.0 < 1.0-sp1 < 1.0.1`. A version splits into numbers and qualifiers at `.`, `-` and
 * every switch between digits and letters; numbers compare as numbers, a number beats a qualifier,
 * and a missing part counts as `0` or as the release itself.
 */
object MavenVersion : Comparator<String> {
    override fun compare(
        a: String,
        b: String,
    ): Int {
        val left = tokens(a)
        val right = tokens(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val c = compareItems(left.getOrNull(i), right.getOrNull(i))
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(
        candidate: String,
        than: String,
    ) = compare(candidate, than) > 0

    private fun compareItems(
        a: Any?,
        b: Any?,
    ): Int =
        when {
            a is Long && b is Long -> a.compareTo(b)
            a is Long && b is String -> 1
            a is String && b is Long -> -1
            a is Long && b == null -> a.compareTo(0L)
            a == null && b is Long -> 0L.compareTo(b)
            else -> compareQualifiers(a as String? ?: "", b as String? ?: "")
        }

    private fun compareQualifiers(
        a: String,
        b: String,
    ): Int {
        val rankA = rank(a)
        val rankB = rank(b)
        if (rankA != rankB) return rankA.compareTo(rankB)
        return if (rankA == UNKNOWN) a.compareTo(b) else 0
    }

    private fun rank(qualifier: String): Int =
        when (qualifier) {
            "alpha", "a" -> 0
            "beta", "b" -> 1
            "milestone", "m" -> 2
            "rc", "cr" -> 3
            "snapshot" -> 4
            "", "ga", "final", "release" -> RELEASE
            "sp" -> 6
            else -> UNKNOWN
        }

    private const val RELEASE = 5
    private const val UNKNOWN = 7

    /** Numbers as [Long], qualifiers in lower case; zero-length parts dropped. */
    private fun tokens(version: String): List<Any> {
        val out = ArrayList<Any>()
        val current = StringBuilder()
        var digits = false

        fun flush() {
            if (current.isEmpty()) return
            val text = current.toString()
            out.add(if (digits) text.toLongOrNull() ?: Long.MAX_VALUE else text.lowercase())
            current.clear()
        }

        for (c in version) {
            when {
                c == '.' || c == '-' || c == '_' || c == '+' -> {
                    flush()
                }

                current.isNotEmpty() && c.isDigit() != digits -> {
                    flush()
                    digits = c.isDigit()
                    current.append(c)
                }

                else -> {
                    if (current.isEmpty()) digits = c.isDigit()
                    current.append(c)
                }
            }
        }
        flush()
        // Trailing zeros and release markers add nothing: 1.0 == 1.0.0 == 1.0.0-final.
        while (out.isNotEmpty() && (out.last() == 0L || (out.last() as? String)?.let { rank(it) == RELEASE } == true)) {
            out.removeAt(out.size - 1)
        }
        // Nor do zeros before a qualifier, as Maven trims each sub-list: 1.0.0-rc1 == 1.0-rc1.
        var i = out.size - 1
        while (i > 0) {
            if (out[i] is String) {
                while (i > 0 && out[i - 1] == 0L) {
                    out.removeAt(i - 1)
                    i--
                }
            }
            i--
        }
        return out
    }
}
