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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotificationTest {
    @Test
    fun testPaymentReceivedDeserialization() {
        val json =
            """{"notification_type":"payment_received","notification":{"type":"incoming","invoice":"lnbc50n1...","description":"coffee","preimage":"abc","payment_hash":"hash123","amount":5000,"fees_paid":10,"created_at":1693876497,"expires_at":1694876497,"settled_at":1694876500}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<PaymentReceivedNotification>(notification)
        assertNotNull(notification.notification)
        assertEquals("incoming", notification.notification.type)
        assertEquals("lnbc50n1...", notification.notification.invoice)
        assertEquals("coffee", notification.notification.description)
        assertEquals("abc", notification.notification.preimage)
        assertEquals("hash123", notification.notification.payment_hash)
        assertEquals(5000L, notification.notification.amount)
        assertEquals(10L, notification.notification.fees_paid)
        assertEquals(1693876497L, notification.notification.created_at)
        assertEquals(1694876497L, notification.notification.expires_at)
        assertEquals(1694876500L, notification.notification.settled_at)
    }

    @Test
    fun testPaymentSentDeserialization() {
        val json =
            """{"notification_type":"payment_sent","notification":{"type":"outgoing","invoice":"lnbc100n1...","preimage":"def456","payment_hash":"hash456","amount":10000,"fees_paid":50,"created_at":1693876497,"settled_at":1694876500}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<PaymentSentNotification>(notification)
        assertNotNull(notification.notification)
        assertEquals("outgoing", notification.notification.type)
        assertEquals("lnbc100n1...", notification.notification.invoice)
        assertEquals("def456", notification.notification.preimage)
        assertEquals(10000L, notification.notification.amount)
        assertEquals(50L, notification.notification.fees_paid)
    }

    @Test
    fun testHoldInvoiceAcceptedDeserialization() {
        val json =
            """{"notification_type":"hold_invoice_accepted","notification":{"type":"incoming","invoice":"lnbc200n1...","payment_hash":"hash789","amount":20000,"created_at":1693876497,"expires_at":1694876497,"settle_deadline":800000}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<HoldInvoiceAcceptedNotification>(notification)
        assertNotNull(notification.notification)
        assertEquals("incoming", notification.notification.type)
        assertEquals("lnbc200n1...", notification.notification.invoice)
        assertEquals("hash789", notification.notification.payment_hash)
        assertEquals(20000L, notification.notification.amount)
        assertEquals(800000L, notification.notification.settle_deadline)
        assertEquals(1693876497L, notification.notification.created_at)
        assertEquals(1694876497L, notification.notification.expires_at)
    }

    @Test
    fun testPaymentReceivedMinimalFields() {
        val json = """{"notification_type":"payment_received","notification":{"type":"incoming","amount":100}}"""
        val notification = OptimizedJsonMapper.fromJsonTo<Notification>(json)
        assertIs<PaymentReceivedNotification>(notification)
        assertEquals("incoming", notification.notification?.type)
        assertEquals(100L, notification.notification?.amount)
        assertNull(notification.notification?.invoice)
        assertNull(notification.notification?.preimage)
    }

    @Test
    @Throws(IllegalArgumentException::class)
    fun testUnknownNotificationTypeReturnsNull() {
        val json = """{"notification_type":"unknown_type","notification":{}}"""
        assertFailsWith<IllegalArgumentException> {
            OptimizedJsonMapper.fromJsonTo<Notification>(json)
        }
    }
}
