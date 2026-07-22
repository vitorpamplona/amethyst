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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

/**
 * True when every char of [s] is printable ASCII (0x20–0x7e) and not a JSON
 * metacharacter (`"` / `\`) — i.e. exactly the bytes a JSON string encoder
 * would emit verbatim between the quotes. Frame builders use this to gate a
 * direct-`buildString` fast path against the generic serializer: when it holds
 * the spliced output is byte-identical, and any exotic value (control chars,
 * quotes, non-ASCII) falls back to the escaping serializer.
 */
internal fun isEscapeFreeAscii(s: String): Boolean {
    for (c in s) {
        if (c < ' ' || c > '~' || c == '"' || c == '\\') return false
    }
    return true
}
