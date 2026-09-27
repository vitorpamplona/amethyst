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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.tags.EncryptionTag
import com.vitorpamplona.quartz.nip47WalletConnect.tags.ExtensionsTag
import com.vitorpamplona.quartz.nip47WalletConnect.tags.NotificationsTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagsTest {
    // --- EncryptionTag ---

    @Test
    fun testEncryptionTagParse() {
        val tag = arrayOf("encryption", "nip44_v2", "nip04")
        val result = EncryptionTag.parse(tag)
        assertNotNull(result)
        assertEquals(listOf("nip44_v2", "nip04"), result)
    }

    @Test
    fun testEncryptionTagParseSingleScheme() {
        val tag = arrayOf("encryption", "nip44_v2")
        val result = EncryptionTag.parse(tag)
        assertNotNull(result)
        assertEquals(listOf("nip44_v2"), result)
    }

    @Test
    fun testEncryptionTagParseWrongTagName() {
        val tag = arrayOf("other", "nip44_v2")
        val result = EncryptionTag.parse(tag)
        assertNull(result)
    }

    @Test
    fun testEncryptionTagParseTooShort() {
        val tag = arrayOf("encryption")
        val result = EncryptionTag.parse(tag)
        assertNull(result)
    }

    @Test
    fun testEncryptionTagParseEmptyValue() {
        val tag = arrayOf("encryption", "")
        val result = EncryptionTag.parse(tag)
        assertNull(result)
    }

    @Test
    fun testEncryptionTagAssemble() {
        // NIP-47: one space-separated value, eg. ["encryption", "nip44_v2 nip04"]
        val tag = EncryptionTag.assemble(listOf("nip44_v2", "nip04"))
        assertEquals("encryption", tag[0])
        assertEquals("nip44_v2 nip04", tag[1])
        assertEquals(2, tag.size)
    }

    @Test
    fun testEncryptionTagIsTag() {
        assertTrue(EncryptionTag.isTag(arrayOf("encryption", "nip44_v2")))
        assertFalse(EncryptionTag.isTag(arrayOf("other", "nip44_v2")))
        assertFalse(EncryptionTag.isTag(arrayOf("encryption")))
        assertFalse(EncryptionTag.isTag(arrayOf("encryption", "")))
    }

    // --- NotificationsTag ---

    @Test
    fun testNotificationsTagParse() {
        val tag = arrayOf("notifications", "payment_received", "payment_sent")
        val result = NotificationsTag.parse(tag)
        assertNotNull(result)
        assertEquals(listOf("payment_received", "payment_sent"), result)
    }

    @Test
    fun testNotificationsTagParseSingleType() {
        val tag = arrayOf("notifications", "payment_received")
        val result = NotificationsTag.parse(tag)
        assertNotNull(result)
        assertEquals(listOf("payment_received"), result)
    }

    @Test
    fun testNotificationsTagParseWrongTagName() {
        val tag = arrayOf("other", "payment_received")
        val result = NotificationsTag.parse(tag)
        assertNull(result)
    }

    @Test
    fun testNotificationsTagParseTooShort() {
        val tag = arrayOf("notifications")
        val result = NotificationsTag.parse(tag)
        assertNull(result)
    }

    @Test
    fun testNotificationsTagAssemble() {
        // NWC-02: one space-separated value, eg. ["notifications", "payment_received payment_sent"]
        val tag = NotificationsTag.assemble(listOf("payment_received", "payment_sent", "hold_invoice_accepted"))
        assertEquals("notifications", tag[0])
        assertEquals("payment_received payment_sent hold_invoice_accepted", tag[1])
        assertEquals(2, tag.size)
    }

    // --- ExtensionsTag ---

    @Test
    fun testExtensionsTagAssemble() {
        // NIP-47: ["extensions", "02 03 04"]
        val tag = ExtensionsTag.assemble(listOf("02", "03", "04"))
        assertEquals(2, tag.size)
        assertEquals("extensions", tag[0])
        assertEquals("02 03 04", tag[1])
    }

    @Test
    fun testExtensionsTagParseToleratesMultiElement() {
        assertEquals(listOf("02", "05"), ExtensionsTag.parse(arrayOf("extensions", "02", "05")))
        assertEquals(listOf("02 05"), ExtensionsTag.parse(arrayOf("extensions", "02 05")))
        assertNull(ExtensionsTag.parse(arrayOf("extensions")))
        assertNull(ExtensionsTag.parse(arrayOf("other", "02")))
    }

    @Test
    fun testExtensionForMethod() {
        assertEquals(ExtensionsTag.KEYSEND, ExtensionsTag.forMethod(NwcMethod.PAY_KEYSEND))
        assertEquals(ExtensionsTag.TRANSACTION_HISTORY, ExtensionsTag.forMethod(NwcMethod.LIST_TRANSACTIONS))
        assertEquals(ExtensionsTag.HOLD_INVOICES, ExtensionsTag.forMethod(NwcMethod.MAKE_HOLD_INVOICE))
        assertEquals(ExtensionsTag.HOLD_INVOICES, ExtensionsTag.forMethod(NwcMethod.SETTLE_HOLD_INVOICE))
        assertEquals(ExtensionsTag.HOLD_INVOICES, ExtensionsTag.forMethod(NwcMethod.CANCEL_HOLD_INVOICE))
        assertNull(ExtensionsTag.forMethod(NwcMethod.PAY_INVOICE))
        assertNull(ExtensionsTag.forMethod(NwcMethod.GET_INFO))
    }

    @Test
    fun testNotificationsTagIsTag() {
        assertTrue(NotificationsTag.isTag(arrayOf("notifications", "payment_received")))
        assertFalse(NotificationsTag.isTag(arrayOf("other", "payment_received")))
        assertFalse(NotificationsTag.isTag(arrayOf("notifications")))
        assertFalse(NotificationsTag.isTag(arrayOf("notifications", "")))
    }
}
