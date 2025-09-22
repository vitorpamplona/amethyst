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
package com.vitorpamplona.quartz.nip44Encryption.crypto

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals

class HkdfText {
    companion object {
        val hkdf = Hkdf()
    }

    @Test
    fun testExpand1() {
        /*
        Removed in the conversion to KMP
        val result =
            hkdf.expand(
                "b0a6232d3e5444dbf2291f3290504989716f1dc78ac2da812230720fd76c8f06".hexToByteArray(),
                "c9300041754c97f030ec9c35db607cb642db0f4305f178692954f3a883ea3209".hexToByteArray(),
                76,
            )

        assertEquals("9077071b92dfbd725f5a1dd2dbbf349615f1d0ce22b436e2f505cc05984ef588", result.hmacKey.toHexKey())
        assertEquals("3256a46b762158a6e08338daf2c5b3eefd2bed343cb22df8b6315567b9207cef", result.chachaKey.toHexKey())
        assertEquals("670bb239ac07dbb801653aa5", result.chachaNonce.toHexKey())
*/
        val result2 =
            hkdf.fastExpand(
                "b0a6232d3e5444dbf2291f3290504989716f1dc78ac2da812230720fd76c8f06".hexToByteArray(),
                "c9300041754c97f030ec9c35db607cb642db0f4305f178692954f3a883ea3209".hexToByteArray(),
            )

        assertEquals("9077071b92dfbd725f5a1dd2dbbf349615f1d0ce22b436e2f505cc05984ef588", result2.hmacKey.toHexKey())
        assertEquals("3256a46b762158a6e08338daf2c5b3eefd2bed343cb22df8b6315567b9207cef", result2.chachaKey.toHexKey())
        assertEquals("670bb239ac07dbb801653aa5", result2.chachaNonce.toHexKey())
    }

    @Test
    fun testExpand2() {
        /*
        Removed in the conversion to KMP
        val result =
            hkdf.expand(
                "bc5d4e032696ef107ef1c7b6fc5f00c6e7b31ae4f86ee486ce24aa0d84847d83".hexToByteArray(),
                "830426c471c0870afb0208cabcaa0e4d66fe51af7163336b7b9ec1846c31e900".hexToByteArray(),
                76,
            )

        assertEquals("26ccd9471681fc42459dbf7b14fc54d3f5276ddad482f7c9f81dc021ccbe5592", result.hmacKey.toHexKey())
        assertEquals("a13a519db107cf9ecbca6ba79ab2428fcd624286025b7ee452f153ae770f07a8", result.chachaKey.toHexKey())
        assertEquals("4c8218640c43de928f4c52e1", result.chachaNonce.toHexKey())
         */
        val result2 =
            hkdf.fastExpand(
                "bc5d4e032696ef107ef1c7b6fc5f00c6e7b31ae4f86ee486ce24aa0d84847d83".hexToByteArray(),
                "830426c471c0870afb0208cabcaa0e4d66fe51af7163336b7b9ec1846c31e900".hexToByteArray(),
            )

        assertEquals("26ccd9471681fc42459dbf7b14fc54d3f5276ddad482f7c9f81dc021ccbe5592", result2.hmacKey.toHexKey())
        assertEquals("a13a519db107cf9ecbca6ba79ab2428fcd624286025b7ee452f153ae770f07a8", result2.chachaKey.toHexKey())
        assertEquals("4c8218640c43de928f4c52e1", result2.chachaNonce.toHexKey())
    }

    @Test
    fun testExpand3() {
        /*
        Removed in the conversion to KMP
        val result =
            hkdf.expand(
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                76,
            )

        assertEquals("a30b80c908d01aa28eae9dcdec55ac104f9c889b989ee563919985a84f66360d", result.hmacKey.toHexKey())
        assertEquals("3769af12ff4dbf44e516a22d1d0512e8bc42516d59e8bf401ea346a4d60dccf7", result.chachaKey.toHexKey())
        assertEquals("77938d29bb13ea73f677ac27", result.chachaNonce.toHexKey())
         */
        val result2 =
            hkdf.fastExpand(
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )

        assertEquals("a30b80c908d01aa28eae9dcdec55ac104f9c889b989ee563919985a84f66360d", result2.hmacKey.toHexKey())
        assertEquals("3769af12ff4dbf44e516a22d1d0512e8bc42516d59e8bf401ea346a4d60dccf7", result2.chachaKey.toHexKey())
        assertEquals("77938d29bb13ea73f677ac27", result2.chachaNonce.toHexKey())
    }
}
