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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message

/**
 * `["NEG-ERR", <subId>, <reason>]`, optionally followed by the relay's own
 * `max_sync_events` when the refusal is about result-set size.
 *
 * That fourth element is not in NIP-77, but it is the only way a client learns
 * the one number that decides how to ask again — no NIP-11 field carries it —
 * and it is free for the relay to send, since it must know its own cap to have
 * refused. strfry states it in the prose (`… too many records (2431002 >
 * 1000000)`); [statedCap] reads either form.
 *
 * @property cap the fourth wire element, when present.
 */
class NegErrMessage(
    val subId: String,
    val reason: String,
    val cap: Long? = null,
) : Message {
    override fun label() = LABEL

    /**
     * The relay's negentropy cap if this refusal states one, from the wire
     * field or from the prose, in that order.
     *
     * Only read for a refusal that is about SIZE ([isOverflow]). A quota or
     * rate-limit refusal can carry numbers too, and sizing future windows
     * against one of those would shrink every ask against a relay that has no
     * size limit at all — while the limit that actually refused does not move
     * however small the window gets.
     */
    val statedCap: Long?
        get() = if (!isOverflow(reason)) null else cap?.takeIf { it > 0 } ?: capInReason(reason)

    companion object {
        const val LABEL = "NEG-ERR"

        /** `(2431002 > 1000000)` — the cap is the right-hand side. */
        private val COMPARISON = Regex("""\(\s*\d+\s*>\s*(\d+)\s*\)""")

        /**
         * Does this reason mean "your query matched more than I will
         * reconcile"? — as opposed to any other refusal, which no amount of
         * window splitting will get past.
         */
        fun isOverflow(reason: String): Boolean =
            reason.contains("too many records", ignoreCase = true) ||
                reason.contains("too many results", ignoreCase = true) ||
                reason.contains("too many query results", ignoreCase = true) ||
                reason.contains("result set too large", ignoreCase = true) ||
                reason.contains("results too large", ignoreCase = true) ||
                reason.contains("max_sync_events", ignoreCase = true)

        /** The cap strfry writes into the refusal text, when it is there. */
        fun capInReason(reason: String): Long? =
            COMPARISON
                .find(reason)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
    }
}
