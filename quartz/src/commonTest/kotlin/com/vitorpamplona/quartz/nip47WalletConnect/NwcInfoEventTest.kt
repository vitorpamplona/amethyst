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

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NwcInfoEventTest {
    private val signer = DeterministicSigner("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

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
    fun testEncryptionSchemesSpaceSeparated() {
        // NIP-47 wire format: all schemes in a single space-separated tag value.
        val event =
            NwcInfoEvent("id", "pub", 0L, arrayOf(arrayOf("encryption", "nip44_v2 nip04")), "pay_invoice", "sig")

        val schemes = event.encryptionSchemes()
        assertEquals(2, schemes.size)
        assertTrue(schemes.contains("nip44_v2"))
        assertTrue(schemes.contains("nip04"))
    }

    @Test
    fun testNotificationTypesSpaceSeparated() {
        // NIP-47 wire format: all types in a single space-separated tag value.
        val event =
            NwcInfoEvent(
                "id",
                "pub",
                0L,
                arrayOf(arrayOf("notifications", "payment_received payment_sent")),
                "pay_invoice notifications",
                "sig",
            )

        val types = event.notificationTypes()
        assertEquals(2, types.size)
        assertTrue(types.contains("payment_received"))
        assertTrue(types.contains("payment_sent"))
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

    private fun info(
        content: String,
        vararg tags: Array<String>,
    ) = NwcInfoEvent("id", "pub", 0L, arrayOf(*tags), content, "sig")

    @Test
    fun testSupportsNotificationsViaExtensionsTag() {
        // Post-extensions NIP-47: notifications moved to NWC-02, advertised as `02`,
        // and the content no longer carries the `notifications` token.
        val event = info("pay_invoice get_balance get_info", arrayOf("encryption", "nip44_v2"), arrayOf("extensions", "02 03 04"))
        assertTrue(event.supportsNotifications())
    }

    @Test
    fun testSupportsNotificationsViaNotificationsTag() {
        val event = info("pay_invoice get_info", arrayOf("notifications", "payment_received payment_sent"))
        assertTrue(event.supportsNotifications())
    }

    @Test
    fun testNoNotificationsWhenExtensionsLackIt() {
        val event = info("pay_invoice get_info", arrayOf("extensions", "04 05"))
        assertFalse(event.supportsNotifications())
    }

    @Test
    fun testBuildEmitsSingleValueTags() {
        val template =
            NwcInfoEvent.build(
                listOf("pay_invoice", "get_info"),
                encryptionSchemes = listOf("nip44_v2", "nip04"),
                notificationTypes = listOf("payment_received", "payment_sent"),
                extensions = listOf("02", "05"),
            )
        val event = signer.sign<NwcInfoEvent>(template)

        assertTrue(event.tags.any { it.contentEquals(arrayOf("encryption", "nip44_v2 nip04")) })
        assertTrue(event.tags.any { it.contentEquals(arrayOf("notifications", "payment_received payment_sent")) })
        assertTrue(event.tags.any { it.contentEquals(arrayOf("extensions", "02 05")) })
        assertEquals(listOf("02", "05"), event.extensions())
        assertEquals(listOf("nip44_v2", "nip04"), event.encryptionSchemes())
    }

    @Test
    fun testMayUseExtensionMethod() {
        // Listed in content: allowed regardless of the extensions tag.
        assertTrue(info("pay_invoice list_transactions", arrayOf("extensions", "02")).mayUseExtensionMethod(NwcMethod.LIST_TRANSACTIONS))
        // Advertised extension: allowed even if the content forgot the method.
        assertTrue(info("pay_invoice", arrayOf("extensions", "05")).mayUseExtensionMethod(NwcMethod.LIST_TRANSACTIONS))
        // New-spec wallet that advertises extensions but neither 05 nor the method: skip.
        assertFalse(info("pay_invoice get_info", arrayOf("extensions", "02 03")).mayUseExtensionMethod(NwcMethod.LIST_TRANSACTIONS))
        assertFalse(info("pay_invoice get_info", arrayOf("extensions", "02 03")).mayUseExtensionMethod(NwcMethod.PAY_KEYSEND))
        // Legacy wallet with no extensions tag: keep sending, it answers NOT_IMPLEMENTED if needed.
        assertTrue(info("pay_invoice get_info").mayUseExtensionMethod(NwcMethod.LIST_TRANSACTIONS))
        assertTrue(info("pay_invoice get_info").mayUseExtensionMethod(NwcMethod.PAY_KEYSEND))
        // Core methods always pass.
        assertTrue(info("get_info", arrayOf("extensions", "02")).mayUseExtensionMethod(NwcMethod.PAY_INVOICE))
    }

    @Test
    fun testServerAdvertisesExtensions() {
        val server =
            Nip47Server(
                signer = NostrSignerInternal(signer.key),
                capabilities = listOf(NwcMethod.PAY_INVOICE, NwcMethod.GET_INFO, NwcMethod.PAY_KEYSEND, NwcMethod.LIST_TRANSACTIONS, NwcMethod.MAKE_HOLD_INVOICE),
                notificationTypes = listOf("payment_received"),
            )
        val event = signer.sign<NwcInfoEvent>(server.buildInfoEvent())

        assertEquals(listOf("02", "03", "04", "05"), event.extensions())
        assertTrue(event.tags.any { it.contentEquals(arrayOf("extensions", "02 03 04 05")) })
        // Extension methods SHOULD also be listed in the content.
        assertTrue(event.supportsMethod(NwcMethod.PAY_KEYSEND))
        assertTrue(event.supportsMethod(NwcMethod.LIST_TRANSACTIONS))
        assertTrue(event.supportsNotifications())
    }

    @Test
    fun testServerWithCoreMethodsOnlyHasNoExtensionsTag() {
        val server = Nip47Server(signer = NostrSignerInternal(signer.key), capabilities = listOf(NwcMethod.PAY_INVOICE, NwcMethod.GET_INFO))
        val event = signer.sign<NwcInfoEvent>(server.buildInfoEvent())
        assertFalse(event.advertisesExtensions())
    }
}
