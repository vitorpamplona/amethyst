/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

object MoneroValidator {
    fun isValidAddress(address: String): Boolean {
        val standardAddressRegex = Regex("^4[0-9AB][1-9A-HJ-NP-Za-km-z]{93}$") // 95-char, starts with '4'
        val subAddressRegex = Regex("^8[0-9AB][1-9A-HJ-NP-Za-km-z]{93}$") // 95-char, starts with '8'
        val integratedAddressRegex = Regex("^4[0-9AB][1-9A-HJ-NP-Za-km-z]{104}$") // 106-char, starts with '4'

        return when {
            standardAddressRegex.matches(address) -> true
            subAddressRegex.matches(address) -> true
            integratedAddressRegex.matches(address) -> true
            else -> false
        }
    }
}
