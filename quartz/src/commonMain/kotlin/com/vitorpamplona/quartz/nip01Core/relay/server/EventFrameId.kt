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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The event id of a raw `["EVENT", {...}]` frame, read without parsing it, or null when
 * [frame] is not an EVENT frame or its event carries no 64-hex `id`. For the frames a
 * relay refuses before parsing (an oversized one), which NIP-01 still owes an `OK`.
 * Only the top-level event object's own `id` key counts, never text inside a string.
 */
internal fun eventIdOfEventFrame(frame: String): HexKey? {
    var i = skipSpace(frame, 0)
    if (i >= frame.length || frame[i] != '[') return null
    i = skipSpace(frame, i + 1)
    if (!frame.startsWith("\"EVENT\"", i)) return null
    i = skipSpace(frame, i + 7)
    if (i >= frame.length || frame[i] != ',') return null
    i = skipSpace(frame, i + 1)
    if (i >= frame.length || frame[i] != '{') return null

    // Inside the event object: walk its members, skipping every nested value whole.
    var depth = 1
    var expectKey = true
    i++
    while (i < frame.length) {
        when (val c = frame[i]) {
            '"' -> {
                val end = endOfString(frame, i) ?: return null
                if (depth == 1 && expectKey && end - i == 3 && frame[i + 1] == 'i' && frame[i + 2] == 'd') {
                    var j = skipSpace(frame, end + 1)
                    if (j >= frame.length || frame[j] != ':') return null
                    j = skipSpace(frame, j + 1)
                    if (j >= frame.length || frame[j] != '"') return null
                    val idEnd = endOfString(frame, j) ?: return null
                    val id = frame.substring(j + 1, idEnd)
                    return if (id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' }) id else null
                }
                expectKey = false
                i = end + 1
                continue
            }

            '{', '[' -> {
                depth++
            }

            '}', ']' -> {
                depth--
                if (depth == 0) return null
            }

            ',' -> {
                if (depth == 1) expectKey = true
            }

            else -> {
                if (c == ':' && depth == 1) expectKey = false
            }
        }
        i++
    }
    return null
}

private fun skipSpace(
    s: String,
    from: Int,
): Int {
    var i = from
    while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
    return i
}

/** The index of the quote closing the string opened at [open], or null when it never closes. */
private fun endOfString(
    s: String,
    open: Int,
): Int? {
    var i = open + 1
    while (i < s.length) {
        when (s[i]) {
            '\\' -> i += 2
            '"' -> return i
            else -> i++
        }
    }
    return null
}
