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
package com.vitorpamplona.quartz.buzz.wpWorkspaceProfile

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetWorkspaceProfileEventTest {
    private val admin = NostrSignerInternal(KeyPair())

    // Sign generically and wrap, so the test does not depend on EventFactory registration.
    private suspend fun sealCommand(icon: String?): SetWorkspaceProfileEvent {
        val t = SetWorkspaceProfileEvent.build(icon)
        val signed: Event = admin.sign(t.createdAt, t.kind, t.tags, t.content)
        return SetWorkspaceProfileEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig)
    }

    @Test
    fun setsIconTag() =
        runTest {
            val event = sealCommand("https://example.com/icon.png")

            assertEquals(9033, event.kind)
            assertEquals("", event.content)
            assertEquals("https://example.com/icon.png", event.icon())
        }

    @Test
    fun nullIconClearsTheProfile() =
        runTest {
            val event = sealCommand(null)

            assertNull(event.icon())
            assertTrue(event.tags.none { it[0] == "icon" }, "clearing omits the icon tag")
        }
}
