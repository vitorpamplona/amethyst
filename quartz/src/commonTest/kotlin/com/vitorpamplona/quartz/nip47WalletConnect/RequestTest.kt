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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestTest {
    // --- PayInvoice ---

    @Test
    fun testPayInvoiceCreate() {
        val request = PayInvoiceMethod.create("lnbc50n1...")
        assertEquals(NwcMethod.PAY_INVOICE, request.method)
        assertEquals("lnbc50n1...", request.params?.invoice)
        assertNull(request.params?.amount)
    }

    @Test
    fun testPayInvoiceCreateWithAmount() {
        val request = PayInvoiceMethod.create("lnbc50n1...", 1000L)
        assertEquals(NwcMethod.PAY_INVOICE, request.method)
        assertEquals("lnbc50n1...", request.params?.invoice)
        assertEquals(1000L, request.params?.amount)
    }

    @Test
    fun testPayInvoiceSerialization() {
        val request = PayInvoiceMethod.create("lnbc50n1...")
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"pay_invoice\""))
        assertTrue(json.contains("\"invoice\":\"lnbc50n1...\""))
    }

    @Test
    fun testPayInvoiceDeserialization() {
        val json = """{"method":"pay_invoice","params":{"invoice":"lnbc50n1..."}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayInvoiceMethod>(request)
        assertEquals("lnbc50n1...", request.params?.invoice)
    }

    @Test
    fun testPayInvoiceWithAmountDeserialization() {
        val json = """{"method":"pay_invoice","params":{"invoice":"lnbc50n1...","amount":1000}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayInvoiceMethod>(request)
        assertEquals("lnbc50n1...", request.params?.invoice)
        assertEquals(1000L, request.params?.amount)
    }

    // --- PayKeysend ---

    @Test
    fun testPayKeysendCreate() {
        val request = PayKeysendMethod.create(1000L, "abcdef1234567890")
        assertEquals(NwcMethod.PAY_KEYSEND, request.method)
        assertEquals(1000L, request.params?.amount)
        assertEquals("abcdef1234567890", request.params?.pubkey)
        assertNull(request.params?.preimage)
        assertNull(request.params?.tlv_records)
    }

    @Test
    fun testPayKeysendWithTlvRecords() {
        val tlvRecords = listOf(TlvRecord(7629169L, "hex_value"))
        val request = PayKeysendMethod.create(1000L, "pubkey123", "preimage123", tlvRecords)
        assertEquals(1000L, request.params?.amount)
        assertEquals("pubkey123", request.params?.pubkey)
        assertEquals("preimage123", request.params?.preimage)
        assertNotNull(request.params?.tlv_records)
        assertEquals(1, request.params?.tlv_records?.size)
        assertEquals(7629169L, request.params?.tlv_records?.first()?.type)
        assertEquals("hex_value", request.params?.tlv_records?.first()?.value)
    }

    @Test
    fun testPayKeysendSerialization() {
        val request = PayKeysendMethod.create(1000L, "pubkey123")
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"pay_keysend\""))
        assertTrue(json.contains("\"amount\":1000"))
        assertTrue(json.contains("\"pubkey\":\"pubkey123\""))
    }

    @Test
    fun testPayKeysendDeserialization() {
        val json = """{"method":"pay_keysend","params":{"amount":1000,"pubkey":"pubkey123"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayKeysendMethod>(request)
        assertEquals(1000L, request.params?.amount)
        assertEquals("pubkey123", request.params?.pubkey)
    }

    // --- MakeInvoice ---

    @Test
    fun testMakeInvoiceCreate() {
        val request = MakeInvoiceMethod.create(5000L, "test payment", null, 3600L)
        assertEquals(NwcMethod.MAKE_INVOICE, request.method)
        assertEquals(5000L, request.params?.amount)
        assertEquals("test payment", request.params?.description)
        assertNull(request.params?.description_hash)
        assertEquals(3600L, request.params?.expiry)
    }

    @Test
    fun testMakeInvoiceSerialization() {
        val request = MakeInvoiceMethod.create(5000L, "test")
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"make_invoice\""))
        assertTrue(json.contains("\"amount\":5000"))
    }

    @Test
    fun testMakeInvoiceDeserialization() {
        val json = """{"method":"make_invoice","params":{"amount":5000,"description":"test","expiry":3600}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<MakeInvoiceMethod>(request)
        assertEquals(5000L, request.params?.amount)
        assertEquals("test", request.params?.description)
        assertEquals(3600L, request.params?.expiry)
    }

    // --- LookupInvoice ---

    @Test
    fun testLookupInvoiceByHash() {
        val request = LookupInvoiceMethod.createByHash("abc123")
        assertEquals(NwcMethod.LOOKUP_INVOICE, request.method)
        assertEquals("abc123", request.params?.payment_hash)
        assertNull(request.params?.invoice)
    }

    @Test
    fun testLookupInvoiceByInvoice() {
        val request = LookupInvoiceMethod.createByInvoice("lnbc50n1...")
        assertEquals(NwcMethod.LOOKUP_INVOICE, request.method)
        assertNull(request.params?.payment_hash)
        assertEquals("lnbc50n1...", request.params?.invoice)
    }

    @Test
    fun testLookupInvoiceDeserialization() {
        val json = """{"method":"lookup_invoice","params":{"payment_hash":"abc123"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<LookupInvoiceMethod>(request)
        assertEquals("abc123", request.params?.payment_hash)
    }

    // --- ListTransactions ---

    @Test
    fun testListTransactionsCreate() {
        val request = ListTransactionsMethod.create(from = 1000L, until = 2000L, limit = 10, offset = 0, unpaid = false, type = "incoming")
        assertEquals(NwcMethod.LIST_TRANSACTIONS, request.method)
        assertEquals(1000L, request.params?.from)
        assertEquals(2000L, request.params?.until)
        assertEquals(10, request.params?.limit)
        assertEquals(0, request.params?.offset)
        assertEquals(false, request.params?.unpaid)
        assertEquals("incoming", request.params?.type)
    }

    @Test
    fun testListTransactionsDeserialization() {
        val json = """{"method":"list_transactions","params":{"from":1000,"until":2000,"limit":10}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<ListTransactionsMethod>(request)
        assertEquals(1000L, request.params?.from)
        assertEquals(2000L, request.params?.until)
        assertEquals(10, request.params?.limit)
    }

    // --- GetBalance ---

    @Test
    fun testGetBalanceCreate() {
        val request = GetBalanceMethod.create()
        assertEquals(NwcMethod.GET_BALANCE, request.method)
    }

    @Test
    fun testGetBalanceSerialization() {
        val request = GetBalanceMethod.create()
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"get_balance\""))
    }

    @Test
    fun testGetBalanceDeserialization() {
        val json = """{"method":"get_balance"}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<GetBalanceMethod>(request)
    }

    // --- GetInfo ---

    @Test
    fun testGetInfoCreate() {
        val request = GetInfoMethod.create()
        assertEquals(NwcMethod.GET_INFO, request.method)
    }

    @Test
    fun testGetInfoDeserialization() {
        val json = """{"method":"get_info"}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<GetInfoMethod>(request)
    }

    // --- MakeHoldInvoice ---

    @Test
    fun testMakeHoldInvoiceCreate() {
        val request = MakeHoldInvoiceMethod.create(10000L, "payment_hash_abc", "hold invoice", null, 7200L, 144)
        assertEquals(NwcMethod.MAKE_HOLD_INVOICE, request.method)
        assertEquals(10000L, request.params?.amount)
        assertEquals("payment_hash_abc", request.params?.payment_hash)
        assertEquals("hold invoice", request.params?.description)
        assertEquals(7200L, request.params?.expiry)
        assertEquals(144, request.params?.min_cltv_expiry_delta)
    }

    @Test
    fun testMakeHoldInvoiceDeserialization() {
        val json = """{"method":"make_hold_invoice","params":{"amount":10000,"payment_hash":"abc","description":"test"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<MakeHoldInvoiceMethod>(request)
        assertEquals(10000L, request.params?.amount)
        assertEquals("abc", request.params?.payment_hash)
    }

    // --- CancelHoldInvoice ---

    @Test
    fun testCancelHoldInvoiceCreate() {
        val request = CancelHoldInvoiceMethod.create("payment_hash_abc")
        assertEquals(NwcMethod.CANCEL_HOLD_INVOICE, request.method)
        assertEquals("payment_hash_abc", request.params?.payment_hash)
    }

    @Test
    fun testCancelHoldInvoiceDeserialization() {
        val json = """{"method":"cancel_hold_invoice","params":{"payment_hash":"abc123"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<CancelHoldInvoiceMethod>(request)
        assertEquals("abc123", request.params?.payment_hash)
    }

    // --- SettleHoldInvoice ---

    @Test
    fun testSettleHoldInvoiceCreate() {
        val request = SettleHoldInvoiceMethod.create("preimage_xyz")
        assertEquals(NwcMethod.SETTLE_HOLD_INVOICE, request.method)
        assertEquals("preimage_xyz", request.params?.preimage)
    }

    @Test
    fun testSettleHoldInvoiceDeserialization() {
        val json = """{"method":"settle_hold_invoice","params":{"preimage":"preimage_xyz"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<SettleHoldInvoiceMethod>(request)
        assertEquals("preimage_xyz", request.params?.preimage)
    }

    // --- GetBudget ---

    @Test
    fun testGetBudgetCreate() {
        val request = GetBudgetMethod.create()
        assertEquals(NwcMethod.GET_BUDGET, request.method)
    }

    @Test
    fun testGetBudgetSerialization() {
        val request = GetBudgetMethod.create()
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"get_budget\""))
    }

    @Test
    fun testGetBudgetDeserialization() {
        val json = """{"method":"get_budget"}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<GetBudgetMethod>(request)
    }

    // --- SignMessage ---

    @Test
    fun testSignMessageCreate() {
        val request = SignMessageMethod.create("Hello Nostr")
        assertEquals(NwcMethod.SIGN_MESSAGE, request.method)
        assertEquals("Hello Nostr", request.params?.message)
    }

    @Test
    fun testSignMessageSerialization() {
        val request = SignMessageMethod.create("test message")
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"sign_message\""))
        assertTrue(json.contains("\"message\":\"test message\""))
    }

    @Test
    fun testSignMessageDeserialization() {
        val json = """{"method":"sign_message","params":{"message":"Hello Nostr"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<SignMessageMethod>(request)
        assertEquals("Hello Nostr", request.params?.message)
    }

    // --- CreateConnection ---

    @Test
    fun testCreateConnectionCreate() {
        val request =
            CreateConnectionMethod.create(
                pubkey = "abc123",
                name = "My App",
                requestMethods = listOf("pay_invoice", "get_balance"),
                notificationTypes = listOf("payment_received"),
                maxAmount = 100000L,
                budgetRenewal = "monthly",
            )
        assertEquals(NwcMethod.CREATE_CONNECTION, request.method)
        assertEquals("abc123", request.params?.pubkey)
        assertEquals("My App", request.params?.name)
        assertEquals(listOf("pay_invoice", "get_balance"), request.params?.request_methods)
        assertEquals(listOf("payment_received"), request.params?.notification_types)
        assertEquals(100000L, request.params?.max_amount)
        assertEquals("monthly", request.params?.budget_renewal)
    }

    @Test
    fun testCreateConnectionSerialization() {
        val request = CreateConnectionMethod.create(pubkey = "abc123", name = "My App")
        val json = OptimizedJsonMapper.toJson(request)
        assertTrue(json.contains("\"method\":\"create_connection\""))
        assertTrue(json.contains("\"pubkey\":\"abc123\""))
        assertTrue(json.contains("\"name\":\"My App\""))
    }

    @Test
    fun testCreateConnectionDeserialization() {
        val json =
            """{"method":"create_connection","params":{"pubkey":"abc123","name":"Test App","request_methods":["pay_invoice"],"max_amount":50000,"budget_renewal":"monthly"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<CreateConnectionMethod>(request)
        assertEquals("abc123", request.params?.pubkey)
        assertEquals("Test App", request.params?.name)
        assertEquals(listOf("pay_invoice"), request.params?.request_methods)
        assertEquals(50000L, request.params?.max_amount)
        assertEquals("monthly", request.params?.budget_renewal)
    }

    // --- Unknown method ---

    @Test
    fun testUnknownMethodReturnsNull() {
        val json = """{"method":"unknown_method","params":{}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertNull(request)
    }
}
