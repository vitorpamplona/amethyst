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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `fiat_currencies_accepted`: the ISO 4217 codes the instance trades, comma-separated in one
 * value (`"USD,EUR,ARS"`).
 *
 * other_events.md: "If no currency is specified, all are accepted." Live instances publish the
 * tag with an empty value for that, so an empty value parses to an empty list (every currency),
 * while a missing tag stays null (the accessor cannot tell).
 */
class FiatCurrenciesAcceptedTag {
    companion object {
        const val TAG_NAME = "fiat_currencies_accepted"

        fun parse(tag: Array<String>): List<String>? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun assemble(currencies: List<String>) = arrayOf(TAG_NAME, currencies.joinToString(","))
    }
}
