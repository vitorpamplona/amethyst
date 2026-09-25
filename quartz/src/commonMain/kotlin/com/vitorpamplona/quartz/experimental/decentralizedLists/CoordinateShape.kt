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
package com.vitorpamplona.quartz.experimental.decentralizedLists

/**
 * A cheap, allocation-free check that a value has the `<kind>:<64-hex pubkey>:<d>` coordinate
 * shape, done before anything reaches `AddressSerializer.parse`. That parser splits the string
 * and logs a warning for every value it rejects — and in this family a rejected value is
 * routine: `z` tags legitimately carry plain list names, which may be long and contain colons.
 */
internal object CoordinateShape {
    fun matches(value: String): Boolean {
        val firstColon = value.indexOf(':')
        // kinds are at most 5 digits (0..65535)
        if (firstColon !in 1..5) return false
        for (i in 0 until firstColon) {
            if (value[i] !in '0'..'9') return false
        }
        val pubKeyEnd = firstColon + 1 + 64
        if (value.length <= pubKeyEnd || value[pubKeyEnd] != ':') return false
        for (i in firstColon + 1 until pubKeyEnd) {
            val c = value[i]
            if (c !in '0'..'9' && c !in 'a'..'f' && c !in 'A'..'F') return false
        }
        return true
    }
}
