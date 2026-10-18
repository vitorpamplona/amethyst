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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PrivateZapCheckTest {
    private val alice = NostrSignerSync(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = "b".repeat(64)
    private val note = "c".repeat(64)
    private val otherNote = "d".repeat(64)

    private fun request(vararg tags: Array<String>) = ZapRequestEvent("e".repeat(64), "f".repeat(64), 1_700_000_000, arrayOf(*tags), "", "")

    private fun inner(vararg tags: Array<String>) = PrivateZapEvent.create(alice, arrayOf(*tags), "gm", 1_700_000_000)

    @Test
    fun acceptsAGenuineZapForTheSameTargets() {
        val private = inner(arrayOf("p", bob), arrayOf("e", note))
        assertSame(private, PrivateZapRequestBuilder().checkDecrypted(private, request(arrayOf("p", bob), arrayOf("e", note))))
    }

    @Test
    fun rejectsAZapReusedOnAnotherNote() {
        val private = inner(arrayOf("p", bob), arrayOf("e", note))
        assertFailsWith<IllegalStateException> {
            PrivateZapRequestBuilder().checkDecrypted(private, request(arrayOf("p", bob), arrayOf("e", otherNote)))
        }
    }

    @Test
    fun rejectsAProfileZapRewrappedOntoANote() {
        val profileZap = inner(arrayOf("p", bob))
        assertFailsWith<IllegalStateException> {
            PrivateZapRequestBuilder().checkDecrypted(profileZap, request(arrayOf("p", bob), arrayOf("e", note)))
        }
    }

    @Test
    fun rejectsAZapReusedOnAnotherAddress() {
        val private = inner(arrayOf("p", bob), arrayOf("a", "30023:$bob:one"))
        assertFailsWith<IllegalStateException> {
            PrivateZapRequestBuilder().checkDecrypted(private, request(arrayOf("p", bob), arrayOf("a", "30023:$bob:two")))
        }
    }

    @Test
    fun rejectsAnInnerEventClaimingSomeoneElsesKey() {
        val genuine = inner(arrayOf("p", bob))
        val forged = PrivateZapEvent(genuine.id, "9".repeat(64), genuine.createdAt, genuine.tags, genuine.content, genuine.sig)
        assertFailsWith<IllegalStateException> {
            PrivateZapRequestBuilder().checkDecrypted(forged, request(arrayOf("p", bob)))
        }
    }
}
