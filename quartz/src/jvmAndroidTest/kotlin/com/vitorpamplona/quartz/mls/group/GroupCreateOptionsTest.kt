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
package com.vitorpamplona.quartz.mls.group

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupCreateOptionsTest {
    private val creator = "11".repeat(32).hexToByteArray()

    @Test
    fun aCallerChosenGroupIdIsUsed() {
        val groupId = "my-group".encodeToByteArray()
        val alice = MlsGroup.create(creator, groupId = groupId)
        assertContentEquals(groupId, alice.groupId)

        val bobBundle = alice.createKeyPackage("22".repeat(32).hexToByteArray(), ByteArray(0))
        val bob = MlsGroup.processWelcome(alice.addMember(bobBundle.keyPackage.toTlsBytes()).welcomeBytes!!, bobBundle)
        assertContentEquals(groupId, bob.groupId)
    }

    @Test
    fun aGroupWithoutRequiredCapabilitiesWorks() {
        val alice = MlsGroup.create(creator, requiredCapabilities = null)
        assertTrue(alice.groupContextExtensionsSnapshot().none { it.extensionType == 0x0003 })

        val bobBundle = alice.createKeyPackage("22".repeat(32).hexToByteArray(), ByteArray(0))
        val bob = MlsGroup.processWelcome(alice.addMember(bobBundle.keyPackage.toTlsBytes()).welcomeBytes!!, bobBundle)
        assertEquals(alice.epoch, bob.epoch)
        assertContentEquals("hi".encodeToByteArray(), bob.decrypt(alice.encrypt("hi".encodeToByteArray())).content)
    }
}
