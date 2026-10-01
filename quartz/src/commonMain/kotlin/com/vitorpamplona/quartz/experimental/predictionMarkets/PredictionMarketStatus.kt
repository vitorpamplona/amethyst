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
package com.vitorpamplona.quartz.experimental.predictionMarkets

/** Where a prediction market is in its life, collapsed from the many words publishers use for it. */
enum class PredictionMarketStatus {
    /** Taking bets (or, before that, being funded). */
    OPEN,

    /** No longer taking bets, not resolved yet. */
    CLOSED,

    /** The oracle has named a winning outcome. */
    RESOLVED,

    /** Voided: bets are refunded and no outcome wins. */
    CANCELLED,
    ;

    companion object {
        /** Maps a published `status`/`state` word to a [PredictionMarketStatus], or null when it is not one we know. */
        fun parse(value: String): PredictionMarketStatus? =
            when (value.trim().lowercase()) {
                "active", "open", "funding" -> OPEN
                "resolving", "ended", "closed" -> CLOSED
                "resolved", "settled" -> RESOLVED
                "voided", "void", "cancelled", "canceled" -> CANCELLED
                else -> null
            }
    }
}
