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
package com.vitorpamplona.amethyst.ui.navigation.routes

/**
 * Navigation routes travel as a URI string: `…Route.Room/{id}?message={message}&…`, with every
 * argument percent-encoded into it. Anything the app hands to a route argument therefore has to fit
 * in that string, and there is a hard ceiling on how long the string may get.
 *
 * The ceiling comes from androidx.navigation: it finds a destination by regex-matching the
 * generated route against every destination's pattern, and the path part of that pattern ends in
 * `($|(\?(.)*)|(#(.)*))` — a *capturing* group inside a `*` loop, so the engine pushes one
 * backtracking frame per character of the query. On Android `java.util.regex` is ICU-backed, and
 * ICU caps that stack at 8 MB and then reports **no match** instead of an error, so an oversized
 * route silently matches nothing and NavController throws
 * `IllegalArgumentException: Navigation destination that matches route … cannot be found in the
 * navigation graph`. Measured against ICU with the `Route.Room` pattern, matching starts failing at
 * roughly 100,000 encoded characters.
 *
 * Truncating here keeps navigation working, and it keeps the route out of the range where matching
 * it against every destination in the graph costs real frame time. The budget below is a fifth of
 * the measured ceiling, which leaves room for the rest of the route (the ceiling shifts a little
 * with each destination's own capture-group count) and still carries a diagnostic report of several
 * kilobytes.
 */
const val MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH = 20_000

/** Appended to a value that had to be cut, so the reader knows the tail is missing. */
const val ROUTE_TEXT_ARG_TRUNCATION_MARKER = "\n\n(truncated)"

/**
 * Characters `Uri.encode` leaves alone. Everything else costs three characters (`%XX`) per UTF-8
 * byte, so a single emoji costs twelve.
 */
private const val UNRESERVED = "_-!.~'()*"

/** The widest a single `Char` can encode to: a 3-byte UTF-8 code point that fits in one Char. */
private const val MAX_ENCODED_LENGTH_PER_CHAR = 9

private val TRUNCATION_MARKER_ENCODED_LENGTH = encodedRouteArgLength(ROUTE_TEXT_ARG_TRUNCATION_MARKER)

/**
 * Cuts [this] down so that its percent-encoded form fits in [maxEncodedLength] characters,
 * appending [ROUTE_TEXT_ARG_TRUNCATION_MARKER] when anything was dropped. Values that already fit
 * are returned as-is.
 *
 * Call this on any unbounded text — a shared payload from another app, a crash or resource-usage
 * report, an error message — before it becomes a navigation argument. See
 * [MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH] for what happens when it isn't. The scan stops as soon as the
 * budget is blown, so a megabyte-sized share costs no more than a value at the limit does.
 */
fun String.limitToRouteTextArg(maxEncodedLength: Int = MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH): String {
    // Nothing this short can encode past the budget, so it doesn't need measuring at all.
    if (length <= maxEncodedLength / MAX_ENCODED_LENGTH_PER_CHAR) return this

    val budget = maxEncodedLength - TRUNCATION_MARKER_ENCODED_LENGTH
    var spent = 0
    var index = 0
    var cutAt = 0
    while (index < length) {
        // Steps over whole code points so a surrogate pair is never cut in half.
        val codePoint = codePointAt(index)
        spent += encodedCodePointLength(codePoint)
        if (spent > maxEncodedLength) {
            // Only here is truncation certain. [cutAt] is the last position whose prefix still
            // leaves room for the marker; a budget too small to hold even that yields nothing.
            return if (budget >= 0) substring(0, cutAt) + ROUTE_TEXT_ARG_TRUNCATION_MARKER else ""
        }
        index += Character.charCount(codePoint)
        if (spent <= budget) cutAt = index
    }
    return this
}

/** How many characters [text] takes up in a route once `Uri.encode` has run over it. */
fun encodedRouteArgLength(text: String): Int {
    var total = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        total += encodedCodePointLength(codePoint)
        index += Character.charCount(codePoint)
    }
    return total
}

private fun encodedCodePointLength(codePoint: Int): Int =
    when {
        codePoint < 0x80 && isUnreserved(codePoint.toChar()) -> 1
        codePoint < 0x80 -> 3 // one UTF-8 byte as %XX
        codePoint < 0x800 -> 6
        codePoint < 0x10000 -> 9
        else -> 12 // four UTF-8 bytes, spread over a surrogate pair
    }

private fun isUnreserved(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED
