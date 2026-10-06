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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletRecentEncryptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignerConsentDetailsTest {
    private val vitor = "46".repeat(32)

    @Test
    fun aLoginThatNamesNoRelayIsAnAppLogin() {
        // Brainstorm's sign-in: no relay, just its own tag and a challenge.
        assertTrue(isAppLogin(22242, arrayOf(arrayOf("t", "brainstorm_login"), arrayOf("challenge", "abc"))))
        assertFalse(isAppLogin(22242, arrayOf(arrayOf("relay", "wss://relay.example.com/"), arrayOf("challenge", "abc"))))
        assertFalse(isAppLogin(1, emptyArray()))
    }

    @Test
    fun aSealThisBrokerJustEncryptedShowsItsMessage() {
        val memory = NappletRecentEncryptions()
        val rumor = """{"kind":14,"created_at":1700000000,"tags":[["p","$vitor"]],"content":"BrainstormSignerTest1"}"""
        memory.record("ciphertextABC", vitor, rumor)

        val seal = sealContents(13, "ciphertextABC", memory)
        assertEquals(vitor, seal?.recipient)
        assertEquals(14, seal?.rumor?.kind)
        assertEquals("BrainstormSignerTest1", seal?.rumor?.content)
    }

    @Test
    fun aSealItDidNotEncryptStaysUnknown() {
        assertNull(sealContents(13, "neverSeen", NappletRecentEncryptions()))
        // Not a seal at all: never looked up.
        val memory = NappletRecentEncryptions()
        memory.record("x", vitor, "{}")
        assertNull(sealContents(1, "x", memory))
    }
}
