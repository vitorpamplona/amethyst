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
package com.vitorpamplona.quartz.crypto.nip06

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Bip39KeyPathTest {
    // m/44'/1237'/<account>'/0/0
    private val nip6Base: KeyPath =
        KeyPath("")
            .derive(Hardener.hardened(44L))
            .derive(Hardener.hardened(1237L))

    private fun nip6Path(account: Long): KeyPath =
        nip6Base
            .derive(Hardener.hardened(account))
            .derive(0L)
            .derive(0L)

    @Test
    fun testKeyPath() {
        // m/44'/1237'/<account>'/0/0
        val path = nip6Path(0).path
        assertEquals(2147483692L, path[0])
        assertEquals(2147484885L, path[1])
        assertEquals(2147483648L, path[2])
        assertEquals(0L, path[3])
        assertEquals(0L, path[4])
    }

    @Test
    fun testKeyDecompiledPath() {
        // m/44'/1237'/<account>'/0/0
        val path = KeyPath.computePath("m/44'/1237'/0'/0/0")
        assertEquals(2147483692L, path[0])
        assertEquals(2147484885L, path[1])
        assertEquals(2147483648L, path[2])
        assertEquals(0L, path[3])
        assertEquals(0L, path[4])
    }

    @Test
    fun testHardening() {
        assertEquals(2147483692, Hardener.hardened(44))
    }
}
