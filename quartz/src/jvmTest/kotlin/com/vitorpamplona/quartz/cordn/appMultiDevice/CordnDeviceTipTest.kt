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
package com.vitorpamplona.quartz.cordn.appMultiDevice

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The §6 tip inventory.
 *
 * The tip is the only authenticity root in this design — documents carry no
 * signature — so the cases here are about refusing an inventory a device
 * cannot act on, rather than salvaging part of it.
 */
class CordnDeviceTipTest {
    private val dek = "ab".repeat(32)

    private val inventory =
        CordnTipInventory(
            groups =
                listOf(
                    CordnTipEntry(address = "11".repeat(32), gid = "gid-1"),
                    CordnTipEntry(address = "22".repeat(32), gid = "gid-2"),
                ),
            meta = "33".repeat(32),
            dekPrivateKey = dek,
            servers = listOf("https://first.example", "https://second.example"),
        )

    @Test
    fun `an inventory round-trips through the inner event tags`() {
        assertEquals(inventory, CordnDeviceTip.parse(innerEvent(CordnDeviceTip.tags(inventory))))
    }

    @Test
    fun `server order is preserved, because a reader tries them in order`() {
        val parsed = CordnDeviceTip.parse(innerEvent(CordnDeviceTip.tags(inventory)))

        assertEquals(listOf("https://first.example", "https://second.example"), parsed.servers)
    }

    @Test
    fun `an inventory with no groups is legal`() {
        // An account that holds no cordn groups still has a meta document and
        // a DEK, and a migration of it should succeed with nothing to seed.
        val empty = inventory.copy(groups = emptyList())

        assertEquals(empty, CordnDeviceTip.parse(innerEvent(CordnDeviceTip.tags(empty))))
    }

    @Test
    fun `a missing dek is refused`() {
        // Without it every listed document is unreadable, so there is no
        // partial success to fall back to.
        val tags = CordnDeviceTip.tags(inventory).filterNot { it[0] == CordnDeviceTip.TAG_DEK }.toTypedArray()

        assertThrows(CordnDocumentException::class.java) { CordnDeviceTip.parse(innerEvent(tags)) }
    }

    @Test
    fun `a malformed dek is refused`() {
        val tags = arrayOf<Tag>(arrayOf(CordnDeviceTip.TAG_DEK, "nothex"))

        val thrown = assertThrows(CordnDocumentException::class.java) { CordnDeviceTip.parse(innerEvent(tags)) }
        assertTrue(thrown.message!!.contains("64 hex"))
    }

    @Test
    fun `a group entry with no gid is refused rather than skipped`() {
        // Skipping it would silently drop a group from the migration, which
        // the user discovers as a missing conversation on the new phone.
        val tags =
            arrayOf<Tag>(
                arrayOf(CordnDeviceTip.TAG_X, "11".repeat(32), CordnDeviceTip.KIND_GROUP),
                arrayOf(CordnDeviceTip.TAG_DEK, dek),
            )

        assertThrows(CordnDocumentException::class.java) { CordnDeviceTip.parse(innerEvent(tags)) }
    }

    @Test
    fun `the wrong inner kind is refused`() {
        val wrong = innerEvent(CordnDeviceTip.tags(inventory)).let { Event(it.id, it.pubKey, it.createdAt, 1, it.tags, it.content, it.sig) }

        assertThrows(CordnDocumentException::class.java) { CordnDeviceTip.parse(wrong) }
    }

    @Test
    fun `unknown tags are ignored`() {
        val tags = CordnDeviceTip.tags(inventory) + arrayOf<Tag>(arrayOf("future", "value"))

        assertEquals(inventory, CordnDeviceTip.parse(innerEvent(tags)))
    }

    @Test
    fun `a second meta entry does not displace the first`() {
        val tags =
            CordnDeviceTip.tags(inventory) + arrayOf<Tag>(arrayOf(CordnDeviceTip.TAG_X, "99".repeat(32), CordnDeviceTip.KIND_META))

        assertEquals("33".repeat(32), CordnDeviceTip.parse(innerEvent(tags)).meta)
    }

    @Test
    fun `an inventory with no meta entry parses`() {
        val noMeta = inventory.copy(meta = null)

        assertNull(CordnDeviceTip.parse(innerEvent(CordnDeviceTip.tags(noMeta))).meta)
    }

    @Test
    fun `the dek is normalised to lowercase`() {
        val tags = arrayOf<Tag>(arrayOf(CordnDeviceTip.TAG_DEK, dek.uppercase()))

        assertEquals(dek, CordnDeviceTip.parse(innerEvent(tags)).dekPrivateKey)
    }

    private fun innerEvent(tags: Array<Tag>) =
        Event(
            id = "00".repeat(32),
            pubKey = "cc".repeat(32),
            createdAt = 1,
            kind = CordnDeviceTip.INNER_KIND,
            tags = tags,
            content = "",
            sig = "00".repeat(32),
        )
}
