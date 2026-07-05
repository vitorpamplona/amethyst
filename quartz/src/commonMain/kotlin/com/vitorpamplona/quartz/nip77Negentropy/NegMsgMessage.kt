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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message

class NegMsgMessage(
    val subId: String,
    val message: String,
) : Message {
    override fun label() = LABEL

    /**
     * Wire form is `["NEG-MSG","<subId>","<hex>"]`. The `<hex>` payload is a
     * reconciliation frame that can be ~1 MB and is *always* lowercase hex
     * (`Hex.encode`), so it never needs JSON escaping. The generic serializer
     * still wraps it in a value node and scans every char for escapes it can
     * never find, then re-copies — profiling put that at a large slice of the
     * server's per-round reconcile cost. Build the string directly instead,
     * skipping the scan and the intermediate nodes (measured ~2× faster at the
     * 500 KB frame cap).
     *
     * The fast path only fires when [subId] is plain printable ASCII with no
     * `"`/`\` — exactly the bytes the JSON encoder would emit verbatim — so
     * the output is byte-identical to the generic path. Any exotic subId
     * (control chars, quotes, non-ASCII) falls back to it.
     */
    override fun toJson(): String {
        if (!isEscapeFreeAscii(subId)) return OptimizedJsonMapper.toJson(this)
        return buildString(message.length + subId.length + 16) {
            append("[\"")
            append(LABEL)
            append("\",\"")
            append(subId)
            append("\",\"")
            append(message)
            append("\"]")
        }
    }

    companion object {
        const val LABEL = "NEG-MSG"

        /** True when every char is printable ASCII (0x20–0x7e) and not `"`/`\`. */
        private fun isEscapeFreeAscii(s: String): Boolean {
            for (c in s) {
                if (c < ' ' || c > '~' || c == '"' || c == '\\') return false
            }
            return true
        }
    }
}
