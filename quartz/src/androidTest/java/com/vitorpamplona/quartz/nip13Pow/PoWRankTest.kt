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
package com.vitorpamplona.quartz.nip13Pow

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoWRankTest {
    @Test
    fun setPoW() {
        assertEquals(26, PoWRank.get("00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e"))
    }

    @Test
    fun setPoWIfCommited25() {
        assertEquals(25, PoWRank.getCommited("00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e", 25))
    }

    @Test
    fun setPoWIfCommited26() {
        assertEquals(26, PoWRank.getCommited("00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e", 26))
    }

    @Test
    fun setPoWIfCommited27() {
        assertEquals(26, PoWRank.getCommited("00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e", 27))
    }
}
