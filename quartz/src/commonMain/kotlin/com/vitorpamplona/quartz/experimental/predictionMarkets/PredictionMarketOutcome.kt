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

import androidx.compose.runtime.Immutable

/**
 * One answer a market can resolve to. BAO writes some as a bare word (`["outcome","YES"]`, a
 * JSON string) and some as an id with a label (`["outcome","opt1","Foundry USA"]`,
 * `{"id":"A","label":"Yes"}`): a `resolution` names the [id], a reader is shown the [label].
 */
@Immutable
data class PredictionMarketOutcome(
    val id: String,
    val label: String,
) {
    /** Whether [resolution] names this outcome — by its id, or by its label as some markets do. */
    fun isNamedBy(resolution: String?): Boolean = resolution != null && (id.equals(resolution, ignoreCase = true) || label.equals(resolution, ignoreCase = true))

    companion object {
        /** More than any market offers; a crafted event with thousands is cut here. */
        const val MAX = 64
    }
}
