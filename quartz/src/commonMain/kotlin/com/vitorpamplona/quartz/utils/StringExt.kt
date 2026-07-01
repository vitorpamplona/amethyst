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
package com.vitorpamplona.quartz.utils

expect fun String.internIfPossible(): String

/**
 * Truncates to at most [maxUnits] UTF-16 code units **without splitting a
 * surrogate pair**.
 *
 * A plain [take]/[substring] counts UTF-16 code units, so a cut can land
 * between the two halves of an astral character (e.g. an emoji) and leave a
 * lone surrogate. A lone surrogate is unencodable as UTF-8: it survives an
 * in-memory event-id hash but is replaced by '?' the moment the event is
 * serialized to a relay, so the relay recomputes a different id and rejects the
 * event. Any truncation whose result ends up inside a signed event (alt,
 * summary, description, … tags) must go through here.
 */
fun String.takeKeepingSurrogatePairs(maxUnits: Int): String {
    if (maxUnits <= 0) return ""
    if (length <= maxUnits) return this
    val end = if (this[maxUnits - 1].isHighSurrogate()) maxUnits - 1 else maxUnits
    return substring(0, end)
}
