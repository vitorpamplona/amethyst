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
package com.vitorpamplona.quic.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialSecretsTest {
    /**
     * RFC 9001 Appendix A.1 — Initial Packet test vectors using the canonical
     * client DCID `0x8394c8f03e515708`.
     *
     * Expected derived protection material:
     *   client_initial_key  = 1f369613dd76d5467730efcbe3b1a22d
     *   client_initial_iv   = fa044b2f42a3fd3b46fb255c
     *   client_hp_key       = 9f50449e04a0e810283a1e9933adedd2
     *   server_initial_key  = cf3a5331653c364c88f0f379b6067e37
     *   server_initial_iv   = 0ac1493ca1905853b0bba03e
     *   server_hp_key       = c206b8d9b9f0f37644430b490eeaa314
     */
    @Test
    fun rfc9001_appendix_a1_client_dcid_vectors() {
        val dcid = "8394c8f03e515708".hexToByteArray()
        val p = InitialSecrets.derive(dcid)
        assertEquals("1f369613dd76d5467730efcbe3b1a22d", p.clientKey.toHex())
        assertEquals("fa044b2f42a3fd3b46fb255c", p.clientIv.toHex())
        assertEquals("9f50449e04a0e810283a1e9933adedd2", p.clientHp.toHex())
        assertEquals("cf3a5331653c364c88f0f379b6067e37", p.serverKey.toHex())
        assertEquals("0ac1493ca1905853b0bba03e", p.serverIv.toHex())
        assertEquals("c206b8d9b9f0f37644430b490eeaa314", p.serverHp.toHex())
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
