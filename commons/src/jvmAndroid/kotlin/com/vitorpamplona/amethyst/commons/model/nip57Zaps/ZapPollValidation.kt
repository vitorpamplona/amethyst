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
package com.vitorpamplona.amethyst.commons.model.nip57Zaps

import java.math.BigDecimal

/**
 * Validates and computes values for zap poll voting.
 * Extracted from PollNoteViewModel for cross-platform reuse.
 */
object ZapPollValidation {
    /**
     * Checks whether [amount] is a valid vote amount given the poll's min/max constraints.
     */
    fun isValidVoteAmount(
        amount: BigDecimal?,
        valueMinimum: BigDecimal?,
        valueMaximum: BigDecimal?,
    ): Boolean {
        if (amount == null) return false
        return when {
            valueMinimum == null && valueMaximum == null -> amount > BigDecimal.ZERO
            valueMinimum == null -> amount > BigDecimal.ZERO && amount <= valueMaximum!!
            valueMaximum == null -> amount >= valueMinimum
            else -> amount in valueMinimum..valueMaximum
        }
    }

    /**
     * Long overload for [isValidVoteAmount].
     */
    fun isValidVoteAmount(
        amount: Long?,
        valueMinimum: Long?,
        valueMaximum: Long?,
    ): Boolean {
        if (amount == null) return false
        return when {
            valueMinimum == null && valueMaximum == null -> amount > 0
            valueMinimum == null -> amount > 0 && amount <= valueMaximum!!
            valueMaximum == null -> amount >= valueMinimum
            else -> amount in valueMinimum..valueMaximum
        }
    }

    /**
     * Builds a placeholder hint string for the vote amount field.
     *
     * @param sats the localised "sats" label
     */
    fun voteAmountPlaceholder(
        valueMinimum: Long?,
        valueMaximum: Long?,
        sats: String,
    ): String =
        when {
            valueMinimum == null && valueMaximum == null -> sats
            valueMinimum == null -> "1—$valueMaximum $sats"
            valueMaximum == null -> ">$valueMinimum $sats"
            else -> "$valueMinimum—$valueMaximum $sats"
        }

    /**
     * Parses a user-entered text amount to Long, returning null for empty or non-numeric input.
     */
    fun parseVoteAmount(textAmount: String): Long? =
        if (textAmount.isEmpty()) {
            null
        } else {
            textAmount.toLongOrNull()
        }

    /**
     * From a list of predefined zap amounts, returns only those that are valid for this poll,
     * plus the poll's min and max boundaries. The result is sorted and deduplicated.
     */
    fun filterZapChoices(
        zapPaymentChoices: List<Long>,
        valueMinimum: Long?,
        valueMaximum: Long?,
    ): List<Long> {
        val options =
            zapPaymentChoices
                .filter { isValidVoteAmount(it, valueMinimum, valueMaximum) }
                .toMutableList()

        if (options.isEmpty() && valueMinimum != null && valueMaximum != null && valueMinimum != valueMaximum) {
            options.add((valueMinimum + valueMaximum) / 2)
        }

        valueMinimum?.let { options.add(it) }
        valueMaximum?.let { options.add(it) }

        return options.toSet().sorted()
    }
}
