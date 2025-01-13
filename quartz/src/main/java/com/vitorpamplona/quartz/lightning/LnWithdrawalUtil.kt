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
package com.vitorpamplona.quartz.lightning

import java.util.regex.Pattern

object LnWithdrawalUtil {
    private val withdrawalPattern =
        Pattern.compile(
            "lnurl1[02-9ac-hj-np-z]+",
            Pattern.CASE_INSENSITIVE,
        )

    /**
     * Finds LN withdrawal in the provided input string and returns it. For example for input = "aaa
     * bbb lnbc1xxx ccc" it will return "lnbc1xxx" It will only return the first withdrawal found in
     * the input.
     *
     * @return the invoice if it was found. null for null input or if no invoice is found
     */
    fun findWithdrawal(input: String?): String? {
        if (input == null) {
            return null
        }
        val matcher = withdrawalPattern.matcher(input)
        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }
}
