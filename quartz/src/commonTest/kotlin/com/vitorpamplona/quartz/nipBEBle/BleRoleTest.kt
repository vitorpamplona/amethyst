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
package com.vitorpamplona.quartz.nipBEBle

import kotlin.test.Test
import kotlin.test.assertEquals

class BleRoleTest {
    @Test
    fun higherUuidBecomesServer() {
        val high = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"
        val low = "00000000-0000-0000-0000-000000000000"
        assertEquals(BleRole.SERVER, assignRole(high, low))
    }

    @Test
    fun lowerUuidBecomesClient() {
        val high = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"
        val low = "00000000-0000-0000-0000-000000000000"
        assertEquals(BleRole.CLIENT, assignRole(low, high))
    }

    @Test
    fun rolesAreComplementary() {
        val a = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
        val b = "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB"

        val roleA = assignRole(a, b)
        val roleB = assignRole(b, a)

        assertEquals(BleRole.CLIENT, roleA)
        assertEquals(BleRole.SERVER, roleB)
    }

    @Test
    fun permanentServerUuid() {
        val serverUuid = BleConfig.PERMANENT_SERVER_UUID
        val anyOther = "12345678-1234-1234-1234-123456789012"
        assertEquals(BleRole.SERVER, assignRole(serverUuid, anyOther))
    }

    @Test
    fun permanentClientUuid() {
        val clientUuid = BleConfig.PERMANENT_CLIENT_UUID
        val anyOther = "12345678-1234-1234-1234-123456789012"
        assertEquals(BleRole.CLIENT, assignRole(clientUuid, anyOther))
    }

    @Test
    fun caseInsensitiveComparison() {
        val lower = "abcdef12-3456-7890-abcd-ef1234567890"
        val upper = "ABCDEF12-3456-7890-ABCD-EF1234567890"
        val other = "00000000-0000-0000-0000-000000000001"

        assertEquals(assignRole(lower, other), assignRole(upper, other))
    }

    @Test
    fun equalUuidsDefaultToClient() {
        val uuid = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
        assertEquals(BleRole.CLIENT, assignRole(uuid, uuid))
    }
}
