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
package com.vitorpamplona.jsonbench

import java.security.MessageDigest

/** The cross-language digest of HARNESS.md ("Verification: the digest"). */
class EventDigest {
    private val sha = MessageDigest.getInstance("SHA-256")

    private fun field(value: String) {
        sha.update(value.encodeToByteArray())
        sha.update(US)
    }

    fun update(e: Event) {
        field(e.id)
        field(e.pubKey)
        field(e.createdAt.toString())
        field(e.kind.toString())
        for (tag in e.tags) {
            for (value in tag) field(value)
            sha.update(RS)
        }
        sha.update(GS)
        field(e.content)
        field(e.sig)
        sha.update(FS)
    }

    fun hex(): String = sha.digest().joinToString("") { "%02x".format(it) }

    companion object {
        const val US: Byte = 0x1F
        const val RS: Byte = 0x1E
        const val GS: Byte = 0x1D
        const val FS: Byte = 0x1C
    }
}
