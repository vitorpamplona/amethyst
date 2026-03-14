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

import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NwcInfoEventTest {
    private val signer = DeterministicSigner("nsec1w4uucmeyyng0kegm7486r23sv4majkmvqsj6eypprq0xttxss55s5mgg9t".nsecToKeyPair())

    @Test
    fun testBuildInfoEvent() {
        val capabilities = listOf("pay_invoice", "get_balance", "make_invoice", "notifications")
        val template = NwcInfoEvent.build(capabilities)
        val event = signer.sign<NwcInfoEvent>(template)

        assertEquals(NwcInfoEvent.KIND, event.kind)
        assertEquals("pay_invoice get_balance make_invoice notifications", event.content)
    }

    @Test
    fun testCapabilities() {
        val capabilities = listOf("pay_invoice", "get_balance", "make_invoice")
        val template = NwcInfoEvent.build(capabilities)
        val event = signer.sign<NwcInfoEvent>(template)

        val parsed = event.capabilities()
        assertEquals(3, parsed.size)
        assertTrue(parsed.contains("pay_invoice"))
        assertTrue(parsed.contains("get_balance"))
        assertTrue(parsed.contains("make_invoice"))
    }

    @Test
    fun testSupportsMethod() {
        val capabilities = listOf("pay_invoice", "get_balance")
        val template = NwcInfoEvent.build(capabilities)
        val event = signer.sign<NwcInfoEvent>(template)

        assertTrue(event.supportsMethod("pay_invoice"))
        assertTrue(event.supportsMethod("get_balance"))
        assertFalse(event.supportsMethod("make_invoice"))
        assertFalse(event.supportsMethod("pay_keysend"))
    }

    @Test
    fun testSupportsNotifications() {
        val capabilities = listOf("pay_invoice", "notifications")
        val template = NwcInfoEvent.build(capabilities)
        val event = signer.sign<NwcInfoEvent>(template)

        assertTrue(event.supportsNotifications())
    }

    @Test
    fun testDoesNotSupportNotifications() {
        val capabilities = listOf("pay_invoice", "get_balance")
        val template = NwcInfoEvent.build(capabilities)
        val event = signer.sign<NwcInfoEvent>(template)

        assertFalse(event.supportsNotifications())
    }

    @Test
    fun testEncryptionSchemes() {
        val capabilities = listOf("pay_invoice")
        val template = NwcInfoEvent.build(capabilities, encryptionSchemes = listOf("nip44_v2", "nip04"))
        val event = signer.sign<NwcInfoEvent>(template)

        val schemes = event.encryptionSchemes()
        assertEquals(2, schemes.size)
        assertTrue(schemes.contains("nip44_v2"))
        assertTrue(schemes.contains("nip04"))
    }

    @Test
    fun testNotificationTypes() {
        val capabilities = listOf("pay_invoice", "notifications")
        val template = NwcInfoEvent.build(capabilities, notificationTypes = listOf("payment_received", "payment_sent"))
        val event = signer.sign<NwcInfoEvent>(template)

        val types = event.notificationTypes()
        assertEquals(2, types.size)
        assertTrue(types.contains("payment_received"))
        assertTrue(types.contains("payment_sent"))
    }

    @Test
    fun testInfoEventKind() {
        assertEquals(13194, NwcInfoEvent.KIND)
    }

    @Test
    fun testBuildWithNoOptionalTags() {
        val capabilities = listOf("pay_invoice")
        val template = NwcInfoEvent.build(capabilities)
        val event = signer.sign<NwcInfoEvent>(template)

        assertTrue(event.encryptionSchemes().isEmpty())
        assertTrue(event.notificationTypes().isEmpty())
    }
}
