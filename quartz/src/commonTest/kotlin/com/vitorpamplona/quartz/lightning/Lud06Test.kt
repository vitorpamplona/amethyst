/**
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
package com.vitorpamplona.quartz.lightning

import kotlin.test.Test
import kotlin.test.assertEquals

class Lud06Test {
    val lnTips =
        "LNURL1DP68GURN8GHJ7MRW9E6XJURN9UH8WETVDSKKKMN0WAHZ7MRWW4EXCUP0XPURXEFEX9SKGCT9V5ER2V33X4NRGP2NE42"

    @Test()
    fun parseLnUrlp() {
        assertEquals("https://ln.tips/.well-known/lnurlp/0x3e91adaee25215f4", Lud06().toLnUrlp(lnTips))
        assertEquals("0x3e91adaee25215f4@ln.tips", Lud06().toLud16(lnTips))
    }
}
