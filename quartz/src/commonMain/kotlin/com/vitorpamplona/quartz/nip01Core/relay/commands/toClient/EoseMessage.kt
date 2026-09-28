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
 * `["EOSE", <subId>]`, optionally with NIP-67 completeness hints:
 * `["EOSE", <subId>, [<hint>, ...]]`.
 *
 * [hints] is null when the relay sent the two-element form. Hints are only
 * about stored events; their presence is definitive, their absence is not
 * (see [isFinished] / [hasMore]). Unknown hint values are kept but ignored.
 */
class EoseMessage(
    val subId: String,
    val hints: List<String>? = null,
) : Message {
    override fun label() = LABEL

    /** NIP-67 `finish`: every stored match was sent; do not paginate further. */
    fun isFinished() = hints?.contains(HINT_FINISH) == true

    /** NIP-67 `more`: the relay holds more stored matches than it sent; paginate. */
    fun hasMore() = hints?.contains(HINT_MORE) == true

    /** NIP-67 `auth`: more stored matches may be available after NIP-42 AUTH. */
    fun needsAuth() = hints?.contains(HINT_AUTH) == true

    /**
     * Wire form is `["EOSE","<subId>"]` — sent once per REQ, so it is on
     * the per-subscription floor. Splice it directly when [subId] needs no
     * escaping (the common case: client-chosen sub ids are short ASCII),
     * skipping the generic serializer's node tree. Byte-identical output;
     * any exotic subId falls back.
     */
    override fun toJson(): String {
        if (hints != null || !isEscapeFreeAscii(subId)) return super.toJson()
        return buildString(subId.length + 12) {
            append("[\"EOSE\",\"")
            append(subId)
            append("\"]")
        }
    }

    companion object {
        const val LABEL = "EOSE"

        // NIP-67 hint values.
        const val HINT_FINISH = "finish"
        const val HINT_MORE = "more"
        const val HINT_AUTH = "auth"
    }
}
