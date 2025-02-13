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

import com.vitorpamplona.quartz.R

public fun arrayOfNotNull(vararg elements: String?) = removeTrailingNullsAndEmptyOthers(*elements)

public fun removeTrailingNullsAndEmptyOthers(vararg elements: String?): Array<String> {
    val lastNonNullIndex = elements.indexOfLast { it != null }

    if (lastNonNullIndex < 0) return Array(0) { "" }

    return Array(lastNonNullIndex + 1) { index ->
        elements[index] ?: ""
    }
}

fun Array<String>.startsWith(startsWith: Array<String>): Boolean {
    if (startsWith.size > this.size) return false
    for (tagIdx in startsWith.indices) {
        if (startsWith[tagIdx] != this[tagIdx]) return false
    }
    return true
}

public inline fun <T, R> Array<out T>.lastNotNullOfOrNull(transform: (T) -> R?): R? {
    for (index in this.indices.reversed()) {
        val result = transform(this[index])
        if (result != null) {
            return result
        }
    }
    return null
}
