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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.ensure
import com.vitorpamplona.quartz.utils.parseBigDecimalOrNull
import com.vitorpamplona.quartz.utils.toPlainStringValue

/**
 * NIP-69 `bond`: "the bond amount, the bond is a security deposit that both parties must pay."
 *
 * A decimal, because platforms write decimals: RoboSats publishes its `bond_size`, a `Decimal(4,2)`
 * **percentage of the trade** (`"3.00"`, minimum 2%), while Bitblik, Bitway and Bitvyber write
 * `"0"` (2026-10 relay census). NIP-69 names no unit, so none is assumed here: read the platform
 * (`y`) to interpret the value. Unparseable values read as null.
 */
class BondTag {
    companion object {
        const val TAG_NAME = "bond"

        fun parse(tag: Array<String>): BigDecimal? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return parseBigDecimalOrNull(tag[1])
        }

        fun assemble(bond: BigDecimal) = arrayOf(TAG_NAME, bond.toPlainStringValue())

        fun assemble(bond: Long) = assemble(BigDecimal(bond))
    }
}
