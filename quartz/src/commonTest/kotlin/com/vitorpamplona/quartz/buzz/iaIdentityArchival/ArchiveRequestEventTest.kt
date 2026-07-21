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
package com.vitorpamplona.quartz.buzz.iaIdentityArchival

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveRequestEventTest {
    private val target = "a".repeat(64)
    private val successor = "b".repeat(64)

    @Test
    fun buildFullRequestRoundTrips() {
        val owner = KeyPair()
        val auth = OwnerAttestation.sign(target, "", owner.privKey!!)

        val tpl = ArchiveRequestEvent.build(target, "leaving the org", "left-organization", successor, auth)
        val ev = ArchiveRequestEvent("00".repeat(32), "cc".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals(9035, ArchiveRequestEvent.KIND)
        assertEquals(target, ev.target())
        assertEquals("left-organization", ev.reason())
        assertEquals(successor, ev.replacedBy())
        assertEquals("leaving the org", ev.content)
        assertTrue(ev.isProtected(), "archive request must carry the NIP-70 '-' marker")
        assertEquals(owner.pubKey.toHexKey(), ev.auth()?.ownerPubKey)
    }

    @Test
    fun minimalRequestOmitsOptionalTags() {
        val tpl = ArchiveRequestEvent.build(target)
        val ev = ArchiveRequestEvent("00".repeat(32), "cc".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals(target, ev.target())
        assertNull(ev.reason())
        assertNull(ev.replacedBy())
        assertNull(ev.auth())
        assertTrue(ev.isProtected())
    }
}
