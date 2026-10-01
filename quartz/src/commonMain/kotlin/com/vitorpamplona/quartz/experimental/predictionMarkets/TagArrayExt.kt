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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.core.fastForEach

/** The first non-blank value of a tag named [name], or null. */
private fun TagArray.firstNonBlankValue(name: String): String? = fastFirstNotNullOfOrNull { if (it.size > 1 && it[0] == name && it[1].isNotBlank()) it[1] else null }

/** The first non-blank value among [first] and then [second], in that priority order. */
private fun TagArray.firstNonBlankValue(
    first: String,
    second: String,
): String? = firstNonBlankValue(first) ?: firstNonBlankValue(second)

fun TagArray.marketId() = firstNonBlankValue("market")

fun TagArray.marketTitle() = firstNonBlankValue("title")

/** The JSON blob of the `data` tag BAO Markets attaches to its current event shape. */
fun TagArray.marketData() = firstNonBlankValue("data")

fun TagArray.marketType() = firstNonBlankValue("type")

/**
 * The declared lifecycle state, tried by tag name in priority order: `status` first, then the
 * older `state` and the one-letter `s`. BAO Markets writes all three but lets them drift apart
 * (an "active" status next to an "ended" state), and `status` is the one it keeps current.
 */
fun TagArray.marketStatusValues(): List<String> = listOfNotNull(firstNonBlankValue("status"), firstNonBlankValue("state"), firstNonBlankValue("s"))

/** When betting closes, unix seconds. */
fun TagArray.marketEnd() = firstNonBlankValue("end")?.trim()?.toLongOrNull()

fun TagArray.marketNetwork() = firstNonBlankValue("network", "n")

fun TagArray.marketCategory() = firstNonBlankValue("category", "c")

/**
 * Every `outcome` tag, in order, once per id and at most [PredictionMarketOutcome.MAX]. The BAO
 * Fund shape writes `["outcome", id, label]`; the current shape a bare word, its own label.
 */
fun TagArray.marketOutcomes(): List<PredictionMarketOutcome> {
    val result = LinkedHashMap<String, PredictionMarketOutcome>(4)
    fastForEach {
        if (result.size < PredictionMarketOutcome.MAX && it.size > 1 && it[0] == "outcome" && it[1].isNotBlank() && it[1] !in result) {
            result[it[1]] = PredictionMarketOutcome(it[1], it.getOrNull(2)?.ifBlank { null } ?: it[1])
        }
    }
    return result.values.toList()
}

/** The winning outcome, once the market is resolved. */
fun TagArray.marketResolution() = firstNonBlankValue("resolution")

fun TagArray.marketResolutionSource() = firstNonBlankValue("resolution_source")

fun TagArray.marketMinBet() = firstNonBlankValue("min_bet")?.trim()?.toLongOrNull()

fun TagArray.marketMaxBet() = firstNonBlankValue("max_bet")?.trim()?.toLongOrNull()

fun TagArray.marketFeePercent() = firstNonBlankValue("fee_percent")?.trim()?.toDoubleOrNull()

fun TagArray.marketOracle() = firstNonBlankValue("oracle")

fun TagArray.marketCancelReason() = firstNonBlankValue("cancel_reason")

fun TagArray.marketPoolModel() = firstNonBlankValue("pool_model")

fun TagArray.marketClient() = firstNonBlankValue("client")
