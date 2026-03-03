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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignStringTest {
    @Test
    fun signString() {
        val random = "319cc5596fdd6cd767e5a59d976e8e059c61306af90dff1e6ee1067b3a1fdbc0".hexToByteArray()
        val message = "8e58c8251bb406b6ded69e9eb14f55282a9a53bdab16fc49a3218c2ad3abc887".hexToByteArray()
        val keyPair = KeyPair("a5ab474552c8f9c46c2eda5a0b68f27430ad81f96cb405e0cb4e34bf0c6494a2".hexToByteArray())

        val signedMessage = Nip01Crypto.sign(message, keyPair.privKey!!, random).toHexKey()
        val expectedValue = "0f9be7e01ba53d5ee6874b9180c7956269fda7a5be424634c3d17b5cfcea6da001be89183876415ba08b7dafa6cff4555e393dc228fb8769b384344e9a27b77c"
        assertEquals(expectedValue, signedMessage)

        val message2 = "Hello"
        val signedMessage2 = signString(message2, keyPair.privKey!!, random).toHexKey()
        val expectedValue2 = "7ec8194a585bfb513564113b6b7bfeaafa0254c99d24eaf92280657c2291bab908b1b7bc553c83276a0254aef5041bbe6a50e93381edc4de3d859efa1c3a5a1e"
        assertEquals(expectedValue2, signedMessage2)
    }
}
