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

/**
 * Interoperability tests using JSON structures matching Alby Hub (server)
 * and Alby JS SDK (client) NIP-47 implementations.
 * These tests verify that Amethyst can correctly parse responses from Alby wallets.
 */
class AlbyInteropTest {
    // --- Alby Hub pay_invoice response format ---

    @Test
    fun testAlbyPayInvoiceSuccess() {
        val json = """{"result_type":"pay_invoice","result":{"preimage":"6565656565656565656565656565656565656565656565656565656565656565","fees_paid":1}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceSuccessResponse>(response)
        assertEquals("6565656565656565656565656565656565656565656565656565656565656565", response.result?.preimage)
        assertEquals(1L, response.result?.fees_paid)
    }

    @Test
    fun testAlbyPayInvoiceInsufficientBalance() {
        val json = """{"result_type":"pay_invoice","error":{"code":"INSUFFICIENT_BALANCE","message":"insufficient funds available to send"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceErrorResponse>(response)
        assertEquals(NwcErrorCode.INSUFFICIENT_BALANCE, response.error?.code)
        assertEquals("insufficient funds available to send", response.error?.message)
    }

    @Test
    fun testAlbyPayInvoiceBadRequest() {
        val json = """{"result_type":"pay_invoice","error":{"code":"BAD_REQUEST","message":"Failed to decode bolt11 invoice: bolt11 too short"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceErrorResponse>(response)
        assertEquals(NwcErrorCode.BAD_REQUEST, response.error?.code)
    }

    // --- Alby Hub get_balance response format ---

    @Test
    fun testAlbyGetBalanceResponse() {
        val json = """{"result_type":"get_balance","result":{"balance":21000000}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetBalanceSuccessResponse>(response)
        assertEquals(21000000L, response.result?.balance)
    }

    // --- Alby Hub get_info response format (with extended fields) ---

    @Test
    fun testAlbyGetInfoFullResponse() {
        val json =
            """{"result_type":"get_info","result":{"alias":"AlbyHub","color":"#3399ff","pubkey":"02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc","network":"mainnet","block_height":800000,"block_hash":"00000000000000000002a7c4c1e48d76c5a37902165a270156b7a8d72f2e4b10","methods":["pay_invoice","pay_keysend","get_balance","get_budget","get_info","make_invoice","lookup_invoice","list_transactions","sign_message"],"notifications":["payment_received","payment_sent"],"lud16":"satoshi@getalby.com"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetInfoSuccessResponse>(response)
        val result = response.result
        assertNotNull(result)
        assertEquals("AlbyHub", result.alias)
        assertEquals("#3399ff", result.color)
        assertEquals("02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc", result.pubkey)
        assertEquals("mainnet", result.network)
        assertEquals(800000L, result.block_height)
        assertEquals("00000000000000000002a7c4c1e48d76c5a37902165a270156b7a8d72f2e4b10", result.block_hash)
        assertEquals(9, result.methods?.size)
        assertEquals(2, result.notifications?.size)
        assertEquals("satoshi@getalby.com", result.lud16)
    }

    // --- Alby Hub get_budget response format ---

    @Test
    fun testAlbyGetBudgetWithRenewal() {
        val json = """{"result_type":"get_budget","result":{"used_budget":50000,"total_budget":100000,"renews_at":1700000000,"renewal_period":"monthly"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetBudgetSuccessResponse>(response)
        val result = response.result
        assertNotNull(result)
        assertEquals(50000L, result.used_budget)
        assertEquals(100000L, result.total_budget)
        assertEquals(1700000000L, result.renews_at)
        assertEquals("monthly", result.renewal_period)
    }

    @Test
    fun testAlbyGetBudgetUnlimited() {
        val json = """{"result_type":"get_budget","result":{"used_budget":0,"total_budget":0,"renewal_period":"never"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetBudgetSuccessResponse>(response)
        assertEquals(0L, response.result?.used_budget)
        assertEquals(0L, response.result?.total_budget)
        assertNull(response.result?.renews_at)
        assertEquals("never", response.result?.renewal_period)
    }

    // --- Alby Hub make_invoice response format ---

    @Test
    fun testAlbyMakeInvoiceResponse() {
        val json =
            """{"result_type":"make_invoice","result":{"type":"incoming","state":"PENDING","invoice":"lnbc10n1pj3xyz...","description":"Test invoice","payment_hash":"abc123def456","amount":1000,"fees_paid":0,"created_at":1693876497,"expires_at":1694876497}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<MakeInvoiceSuccessResponse>(response)
        val txn = response.result
        assertNotNull(txn)
        assertEquals(NwcTransactionType.INCOMING, txn.type)
        assertEquals(NwcTransactionState.PENDING, txn.state)
        assertEquals("lnbc10n1pj3xyz...", txn.invoice)
        assertEquals("Test invoice", txn.description)
        assertEquals("abc123def456", txn.payment_hash)
        assertEquals(1000L, txn.amount)
        assertEquals(0L, txn.fees_paid)
    }

    // --- Alby Hub lookup_invoice with settled state ---

    @Test
    fun testAlbyLookupInvoiceSettled() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"SETTLED","invoice":"lnbc50n1...","preimage":"preimage123","payment_hash":"hash456","amount":5000,"fees_paid":0,"created_at":1693876497,"settled_at":1694876500}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val txn = response.result
        assertNotNull(txn)
        assertEquals(NwcTransactionType.INCOMING, txn.type)
        assertEquals(NwcTransactionState.SETTLED, txn.state)
        assertEquals("preimage123", txn.preimage)
        assertEquals(1694876500L, txn.settled_at)
    }

    @Test
    fun testAlbyLookupInvoiceNotFound() {
        val json = """{"result_type":"lookup_invoice","error":{"code":"NOT_FOUND","message":"transaction not found"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<NwcErrorResponse>(response)
        assertEquals(NwcErrorCode.NOT_FOUND, response.error?.code)
    }

    // --- Alby Hub list_transactions with total_count ---

    @Test
    fun testAlbyListTransactionsWithTotalCount() {
        val json =
            """{"result_type":"list_transactions","result":{"transactions":[{"type":"incoming","state":"SETTLED","invoice":"lnbc1...","payment_hash":"h1","amount":1000,"fees_paid":0,"created_at":1693876497,"settled_at":1694876500},{"type":"outgoing","state":"SETTLED","invoice":"lnbc2...","payment_hash":"h2","amount":2000,"fees_paid":10,"created_at":1693876400,"settled_at":1694876400}],"total_count":100}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<ListTransactionsSuccessResponse>(response)
        assertEquals(2, response.result?.transactions?.size)
        assertEquals(100L, response.result?.total_count)

        val first = response.result?.transactions?.get(0)
        assertEquals(NwcTransactionType.INCOMING, first?.type)
        assertEquals(NwcTransactionState.SETTLED, first?.state)

        val second = response.result?.transactions?.get(1)
        assertEquals(NwcTransactionType.OUTGOING, second?.type)
        assertEquals(10L, second?.fees_paid)
    }

    // --- Alby Hub sign_message response ---

    @Test
    fun testAlbySignMessageResponse() {
        val json = """{"result_type":"sign_message","result":{"message":"Hello Nostr","signature":"3045022100..."}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<SignMessageSuccessResponse>(response)
        assertEquals("Hello Nostr", response.result?.message)
        assertEquals("3045022100...", response.result?.signature)
    }

    // --- Alby Hub create_connection response ---

    @Test
    fun testAlbyCreateConnectionResponse() {
        val json = """{"result_type":"create_connection","result":{"wallet_pubkey":"02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<CreateConnectionSuccessResponse>(response)
        assertEquals("02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc", response.result?.wallet_pubkey)
    }

    // --- Alby Hub notification formats ---

    @Test
    fun testAlbyPaymentReceivedNotification() {
        val json =
            """{"notification_type":"payment_received","notification":{"type":"incoming","state":"SETTLED","invoice":"lnbc50n1...","preimage":"pre123","payment_hash":"hash123","amount":5000,"fees_paid":0,"created_at":1693876497,"settled_at":1694876500}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<PaymentReceivedNotification>(notification)
        val txn = notification.notification
        assertNotNull(txn)
        assertEquals(NwcTransactionType.INCOMING, txn.type)
        assertEquals(NwcTransactionState.SETTLED, txn.state)
        assertEquals(5000L, txn.amount)
    }

    @Test
    fun testAlbyPaymentSentNotification() {
        val json =
            """{"notification_type":"payment_sent","notification":{"type":"outgoing","state":"SETTLED","invoice":"lnbc100n1...","preimage":"pre456","payment_hash":"hash456","amount":10000,"fees_paid":10,"created_at":1693876497,"settled_at":1694876500}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<PaymentSentNotification>(notification)
        val txn = notification.notification
        assertNotNull(txn)
        assertEquals(NwcTransactionType.OUTGOING, txn.type)
        assertEquals(NwcTransactionState.SETTLED, txn.state)
        assertEquals(10000L, txn.amount)
        assertEquals(10L, txn.fees_paid)
    }

    @Test
    fun testAlbyHoldInvoiceAcceptedNotification() {
        val json =
            """{"notification_type":"hold_invoice_accepted","notification":{"type":"incoming","invoice":"lnbc200n1...","payment_hash":"hash789","amount":20000,"created_at":1693876497,"expires_at":1694876497,"settle_deadline":800000}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<HoldInvoiceAcceptedNotification>(notification)
        val data = notification.notification
        assertNotNull(data)
        assertEquals("incoming", data.type)
        assertEquals(20000L, data.amount)
        assertEquals(800000L, data.settle_deadline)
    }

    // --- Alby Hub error code interop ---

    @Test
    fun testAlbyRestricted() {
        val json = """{"result_type":"pay_invoice","error":{"code":"RESTRICTED","message":"This app does not have the pay_invoice scope"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceErrorResponse>(response)
        assertEquals(NwcErrorCode.RESTRICTED, response.error?.code)
    }

    @Test
    fun testAlbyExpiredConnection() {
        val json = """{"result_type":"get_balance","error":{"code":"EXPIRED","message":"This app has expired"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<NwcErrorResponse>(response)
        assertEquals(NwcErrorCode.EXPIRED, response.error?.code)
    }

    @Test
    fun testAlbyQuotaExceeded() {
        val json = """{"result_type":"pay_invoice","error":{"code":"QUOTA_EXCEEDED","message":"Exceeded budget limit"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceErrorResponse>(response)
        assertEquals(NwcErrorCode.QUOTA_EXCEEDED, response.error?.code)
    }

    // --- Alby Hub request format interop ---

    @Test
    fun testAlbyPayInvoiceRequestFormat() {
        val json = """{"method":"pay_invoice","params":{"invoice":"lnbc10u1pj3xyz..."}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayInvoiceMethod>(request)
        assertEquals("lnbc10u1pj3xyz...", request.params?.invoice)
    }

    @Test
    fun testAlbyGetBudgetRequestFormat() {
        val json = """{"method":"get_budget","params":{}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<GetBudgetMethod>(request)
    }

    @Test
    fun testAlbySignMessageRequestFormat() {
        val json = """{"method":"sign_message","params":{"message":"Hello from Amethyst"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<SignMessageMethod>(request)
        assertEquals("Hello from Amethyst", request.params?.message)
    }

    @Test
    fun testAlbyCreateConnectionRequestFormat() {
        val json =
            """{"method":"create_connection","params":{"pubkey":"02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc","name":"Amethyst","request_methods":["pay_invoice","get_balance","get_info","make_invoice","lookup_invoice","list_transactions"],"notification_types":["payment_received","payment_sent"],"max_amount":100000,"budget_renewal":"monthly","isolated":false}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<CreateConnectionMethod>(request)
        assertEquals("02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc", request.params?.pubkey)
        assertEquals("Amethyst", request.params?.name)
        assertEquals(6, request.params?.request_methods?.size)
        assertEquals(2, request.params?.notification_types?.size)
        assertEquals(100000L, request.params?.max_amount)
        assertEquals("monthly", request.params?.budget_renewal)
        assertEquals(false, request.params?.isolated)
    }

    // --- Alby Hub real bolt11 test vectors ---

    @Test
    fun testAlbyRealBolt11PayInvoiceRequest() {
        val json =
            """{"method":"pay_invoice","params":{"invoice":"lntbs1230n1pnkqautdqyw3jsnp4q09a0z84kg4a2m38zjllw43h953fx5zvqe8qxfgw694ymkq26u8zcpp5yvnh6hsnlnj4xnuh2trzlnunx732dv8ta2wjr75pdfxf6p2vlyassp5hyeg97a3ft5u769kjwsn7p0e85h79pzz8kladmnqhpcypz2uawjs9qyysgqcqpcxq8zals8sq9yeg2pa9eywkgj50cyzxd5elatujuc0c0wh6j9nat5mn34pgk8u9ufpgs99tw9ldlfk42cqlkr48au3lmuh09269prg4qkggh4a8cyqpfl0y6j","metadata":{"a":123}}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayInvoiceMethod>(request)
        assertNotNull(request.params?.invoice)
        assertNotNull(request.params?.metadata)
    }

    @Test
    fun testAlbyPayKeysendWithTlvRecords() {
        val json =
            """{"method":"pay_keysend","params":{"amount":123000,"pubkey":"123pubkey2","preimage":"018465013e2337234a7e5530a21c4a8cf70d84231f4a8ff0b1e2cce3cb2bd03b","tlv_records":[{"type":5482373484,"value":"fajsn341414fq"}]}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayKeysendMethod>(request)
        assertEquals(123000L, request.params?.amount)
        assertEquals("123pubkey2", request.params?.pubkey)
        assertEquals("018465013e2337234a7e5530a21c4a8cf70d84231f4a8ff0b1e2cce3cb2bd03b", request.params?.preimage)
        assertNotNull(request.params?.tlv_records)
        assertEquals(1, request.params?.tlv_records?.size)
        assertEquals(
            5482373484L,
            request.params
                ?.tlv_records
                ?.first()
                ?.type,
        )
        assertEquals(
            "fajsn341414fq",
            request.params
                ?.tlv_records
                ?.first()
                ?.value,
        )
    }

    @Test
    fun testAlbyMakeInvoiceWithNestedMetadata() {
        val json =
            """{"method":"make_invoice","params":{"amount":1000,"description":"Hello, world","expiry":3600,"metadata":{"a":1,"b":"2","c":{"d":3,"e":[{"f":"g"},{"h":"i"}]}}}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<MakeInvoiceMethod>(request)
        assertEquals(1000L, request.params?.amount)
        assertEquals("Hello, world", request.params?.description)
        assertEquals(3600L, request.params?.expiry)
        assertNotNull(request.params?.metadata)
    }

    @Test
    fun testAlbyMakeHoldInvoiceWithPaymentHash() {
        val json =
            """{"method":"make_hold_invoice","params":{"amount":1000,"description":"Hello, world","payment_hash":"1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef","expiry":3600}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<MakeHoldInvoiceMethod>(request)
        assertEquals(1000L, request.params?.amount)
        assertEquals("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", request.params?.payment_hash)
        assertEquals("Hello, world", request.params?.description)
    }

    @Test
    fun testAlbySettleHoldInvoiceWithPreimage() {
        val json =
            """{"method":"settle_hold_invoice","params":{"preimage":"1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<SettleHoldInvoiceMethod>(request)
        assertEquals("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", request.params?.preimage)
    }

    @Test
    fun testAlbyListTransactionsWithUnpaidFilters() {
        val json = """{"method":"list_transactions","params":{"from":0,"until":0,"limit":10,"offset":0,"unpaid_outgoing":true}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<ListTransactionsMethod>(request)
        assertEquals(10, request.params?.limit)
        assertEquals(true, request.params?.unpaid_outgoing)
    }

    @Test
    fun testAlbyCreateConnectionIsolated() {
        val json =
            """{"method":"create_connection","params":{"pubkey":"02a1633cafcc01ebfb6d78e39f687a1f0995c62fc95f51ead10a02ee0be551b5dc","name":"Test 123","request_methods":["get_info","pay_invoice"],"notification_types":["payment_received"],"max_amount":100000000,"budget_renewal":"monthly","isolated":true}}"""
        val request = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<CreateConnectionMethod>(request)
        assertEquals("Test 123", request.params?.name)
        assertEquals(true, request.params?.isolated)
        assertEquals(100000000L, request.params?.max_amount)
        assertEquals("monthly", request.params?.budget_renewal)
        assertEquals(listOf("get_info", "pay_invoice"), request.params?.request_methods)
        assertEquals(listOf("payment_received"), request.params?.notification_types)
    }

    // --- Transaction with settle_deadline from Alby hold invoice ---

    @Test
    fun testAlbyMakeHoldInvoiceWithSettleDeadline() {
        val json =
            """{"result_type":"make_hold_invoice","result":{"type":"incoming","state":"PENDING","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"expires_at":2000,"settle_deadline":144}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<MakeHoldInvoiceSuccessResponse>(response)
        val txn = response.result
        assertNotNull(txn)
        assertEquals(144L, txn.settle_deadline)
        assertEquals(NwcTransactionState.PENDING, txn.state)
    }

    // ===================================================================
    // Alby JS SDK (client) interop tests
    // The JS SDK uses lowercase transaction states while Hub uses uppercase.
    // Both formats must be handled correctly.
    // ===================================================================

    @Test
    fun testJsSdkLowercaseSettledState() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash123","amount":1000,"settled_at":1694876497}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        assertEquals("settled", response.result?.state)
        assertTrue(NwcTransactionState.isSettled(response.result?.state))
    }

    @Test
    fun testJsSdkLowercasePendingState() {
        val json =
            """{"result_type":"make_invoice","result":{"type":"incoming","state":"pending","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<MakeInvoiceSuccessResponse>(response)
        assertEquals("pending", response.result?.state)
        assertTrue(NwcTransactionState.isPending(response.result?.state))
    }

    @Test
    fun testJsSdkLowercaseStatesInListTransactions() {
        val json =
            """{"result_type":"list_transactions","result":{"transactions":[{"type":"incoming","state":"settled","amount":1000,"created_at":1000},{"type":"outgoing","state":"failed","amount":2000,"created_at":2000},{"type":"incoming","state":"accepted","amount":3000,"created_at":3000}],"total_count":3}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<ListTransactionsSuccessResponse>(response)
        val txns = response.result?.transactions
        assertNotNull(txns)
        assertEquals(3, txns.size)
        assertTrue(NwcTransactionState.isSettled(txns[0].state))
        assertTrue(NwcTransactionState.isFailed(txns[1].state))
        assertTrue(NwcTransactionState.isAccepted(txns[2].state))
    }

    @Test
    fun testJsSdkLowercaseStatesInNotification() {
        val json =
            """{"notification_type":"payment_received","notification":{"type":"incoming","state":"settled","invoice":"lnbc...","amount":5000,"created_at":1000,"settled_at":2000}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<PaymentReceivedNotification>(notification)
        assertTrue(NwcTransactionState.isSettled(notification.notification?.state))
    }

    @Test
    fun testJsSdkEmptyGetBudgetResponse() {
        // JS SDK allows get_budget to return empty object when no budget is set
        val json = """{"result_type":"get_budget","result":{}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetBudgetSuccessResponse>(response)
        assertNull(response.result?.used_budget)
        assertNull(response.result?.total_budget)
        assertNull(response.result?.renews_at)
        assertNull(response.result?.renewal_period)
    }

    @Test
    fun testJsSdkGetBudgetWithAllRenewalPeriods() {
        for (period in listOf("daily", "weekly", "monthly", "yearly", "never")) {
            val json = """{"result_type":"get_budget","result":{"used_budget":0,"total_budget":100000,"renewal_period":"$period"}}"""
            val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
            assertIs<GetBudgetSuccessResponse>(response)
            assertEquals(period, response.result?.renewal_period)
        }
    }

    @Test
    fun testJsSdkTransactionWithMetadata() {
        // JS SDK supports structured metadata with comment, payer_data, nostr fields
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"settled_at":2000,"metadata":{"comment":"Thanks!","payer_data":{"name":"Alice","pubkey":"abc123"},"nostr":{"pubkey":"npub1...","tags":[["p","def456"]]}}}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        assertNotNull(response.result?.metadata)
    }

    @Test
    fun testMetadataParserComment() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"settled_at":2000,"metadata":{"comment":"Great post!"}}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val parsed = response.result?.parsedMetadata()
        assertNotNull(parsed)
        assertEquals("Great post!", parsed.comment)
        assertNull(parsed.payerData)
        assertNull(parsed.nostr)
    }

    @Test
    fun testMetadataParserPayerData() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"settled_at":2000,"metadata":{"payer_data":{"name":"Alice","email":"alice@example.com","pubkey":"abc123"}}}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val parsed = response.result?.parsedMetadata()
        assertNotNull(parsed)
        assertEquals("Alice", parsed.payerData?.name)
        assertEquals("alice@example.com", parsed.payerData?.email)
        assertEquals("abc123", parsed.payerData?.pubkey)
        assertEquals("Alice", parsed.senderDisplayName())
    }

    @Test
    fun testMetadataParserNostrZap() {
        val senderHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
        val recipientHex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":21000,"created_at":1000,"settled_at":2000,"metadata":{"nostr":{"pubkey":"$senderHex","tags":[["p","$recipientHex"],["amount","21000"]]}}}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val parsed = response.result?.parsedMetadata()
        assertNotNull(parsed)
        assertEquals(senderHex, parsed.senderPubkeyHex())
        assertEquals(recipientHex, parsed.recipientPubkeyHex())
    }

    @Test
    fun testMetadataParserRecipientData() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"outgoing","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"settled_at":2000,"metadata":{"recipient_data":{"identifier":"alice@getalby.com"}}}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val parsed = response.result?.parsedMetadata()
        assertNotNull(parsed)
        assertEquals("alice@getalby.com", parsed.recipientIdentifier())
    }

    @Test
    fun testMetadataParserNullForSimpleMetadata() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"settled_at":2000,"metadata":{"a":123}}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val parsed = response.result?.parsedMetadata()
        assertNull(parsed)
    }

    @Test
    fun testMetadataParserNullMetadata() {
        val json =
            """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"settled_at":2000}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        val parsed = response.result?.parsedMetadata()
        assertNull(parsed)
    }

    @Test
    fun testJsSdkGetInfoWithAllMethods() {
        // JS SDK advertises all 13 single methods + notifications
        val json =
            """{"result_type":"get_info","result":{"alias":"TestNode","methods":["get_info","get_balance","get_budget","make_invoice","pay_invoice","pay_keysend","lookup_invoice","list_transactions","sign_message","create_connection","make_hold_invoice","settle_hold_invoice","cancel_hold_invoice"],"notifications":["payment_received","payment_sent","hold_invoice_accepted"]}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetInfoSuccessResponse>(response)
        assertEquals(13, response.result?.methods?.size)
        assertEquals(3, response.result?.notifications?.size)
        assertEquals(response.result?.methods?.contains(NwcMethod.GET_BUDGET), true)
        assertEquals(response.result?.methods?.contains(NwcMethod.SIGN_MESSAGE), true)
        assertEquals(response.result?.methods?.contains(NwcMethod.CREATE_CONNECTION), true)
    }
}
