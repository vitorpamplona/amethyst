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
package com.vitorpamplona.quartz.nip55AndroidSigner

import java.security.MessageDigest
import kotlin.test.assertEquals

/**
 * A SHA-256 over every outcome a suite produces, pinned to the value it had when the same suite
 * also compared each outcome with the Jackson code these readers and writers replaced. A change
 * to any one parse or write changes the digest, without keeping Jackson around to say so.
 *
 * An empty [check] expectation prints the digest instead, for recording a new suite.
 */
class Pin(
    var name: String,
) {
    private val md = MessageDigest.getInstance("SHA-256")
    var count = 0
        private set

    fun add(outcome: String?) {
        md.update((outcome ?: "<rejected>").toByteArray())
        md.update(0)
        count++
    }

    fun check(expected: String) {
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (expected.isEmpty()) {
            println("PIN $name $got ($count outcomes)")
        } else {
            assertEquals(expected, got, "$name: one of its $count outcomes changed from what was verified against Jackson")
        }
    }
}
