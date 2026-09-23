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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.tree.Lifetime
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyPackageLifetimeTest {
    private val alice = MlsGroup.create("11".repeat(32).hexToByteArray())
    private val bob = "22".repeat(32).hexToByteArray()

    @Test
    fun theDefaultIsTheBoundedMarmotWindow() {
        val lifetime =
            assertNotNull(
                alice
                    .createKeyPackage(bob, ByteArray(0))
                    .keyPackage.leafNode.lifetime,
            )
        assertTrue(lifetime.notAfter - lifetime.notBefore <= 84L * 24 * 3600 + 3600)
    }

    @Test
    fun aCallerChosenLifetimeIsUsedAndTheMemberCanBeAdded() {
        val now = TimeUtils.now()
        val chosen = Lifetime(now - 3600, now + 365L * 24 * 3600)

        val bundle = alice.createKeyPackage(bob, ByteArray(0), lifetime = chosen)
        assertEquals(chosen, bundle.keyPackage.leafNode.lifetime)

        val result = alice.addMember(bundle.keyPackage.toTlsBytes())
        val joined = MlsGroup.processWelcome(result.welcomeBytes!!, bundle)
        assertEquals(alice.epoch, joined.epoch)
    }
}
