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
package com.vitorpamplona.quartz.utils

import kotlin.math.min

fun String.bytesUsedInMemory(): Int {
    return (8 * ((((this.length) * 2) + 45) / 8))
}

fun String.containsIgnoreCase(term: String): Boolean {
    if (term.isEmpty()) return true // Empty string is contained

    val whatUppercase = term.uppercase()
    val whatLowercase = term.lowercase()

    return containsIgnoreCase(whatLowercase, whatUppercase)
}

fun String.containsIgnoreCase(
    whatLowercase: String,
    whatUppercase: String,
): Boolean {
    var myOffset: Int
    var whatOffset: Int
    val termLength = min(whatUppercase.length, whatLowercase.length)

    for (i in 0..this.length - termLength) {
        if (this[i] != whatLowercase[0] && this[i] != whatUppercase[0]) continue

        myOffset = i + 1
        whatOffset = 1
        while (whatOffset < termLength) {
            if (
                this[myOffset] != whatUppercase[whatOffset] && this[myOffset] != whatLowercase[whatOffset]
            ) {
                break
            }
            myOffset++
            whatOffset++
        }
        if (whatOffset == termLength) return true
    }
    return false
}

fun String.containsAny(terms: List<DualCase>): Boolean {
    if (terms.isEmpty()) return true // Empty string is contained

    if (terms.size == 1) {
        return containsIgnoreCase(terms[0].lowercase, terms[0].uppercase)
    }

    return terms.any { containsIgnoreCase(it.lowercase, it.uppercase) }
}

class DualCase(val lowercase: String, val uppercase: String)
