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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LnZapPaymentRequestEventTest {
    @Test
    fun testEventKind() {
        assertEquals(23194, LnZapPaymentRequestEvent.KIND)
    }

    @Test
    fun testCreatePayInvoiceRequest() =
        runTest {
            val clientKeyPair = KeyPair()
            val walletKeyPair = KeyPair()
            val clientSigner = NostrSignerInternal(clientKeyPair)
            val walletServicePubkey: HexKey =
                walletKeyPair.pubKey.toHexKey()

            val event =
                LnZapPaymentRequestEvent.create(
                    lnInvoice = "lnbc50n1...",
                    walletServicePubkey = walletServicePubkey,
                    signer = clientSigner,
                    createdAt = 1000L,
                )

            assertEquals(23194, event.kind)
            assertEquals(walletServicePubkey, event.walletServicePubKey())
            assertTrue(event.isContentEncoded())
            assertNotNull(event.content)
            assertTrue(event.content.isNotEmpty())
        }

    @Test
    fun testCreateGenericRequest() =
        runTest {
            val clientKeyPair = KeyPair()
            val walletKeyPair = KeyPair()
            val clientSigner = NostrSignerInternal(clientKeyPair)
            val walletServicePubkey: HexKey =
                walletKeyPair.pubKey.toHexKey()

            val request = GetBalanceMethod.create()
            val event =
                LnZapPaymentRequestEvent.createRequest(
                    request = request,
                    walletServicePubkey = walletServicePubkey,
                    signer = clientSigner,
                    createdAt = 1000L,
                )

            assertEquals(23194, event.kind)
            assertEquals(walletServicePubkey, event.walletServicePubKey())
            // Should not have encryption tag for NIP-04
            assertNull(event.encryptionScheme())
        }

    @Test
    fun testDecryptPayInvoiceRequest() =
        runTest {
            val clientKeyPair = KeyPair()
            val walletKeyPair = KeyPair()
            val clientSigner = NostrSignerInternal(clientKeyPair)
            val walletSigner = NostrSignerInternal(walletKeyPair)
            val walletServicePubkey: HexKey =
                walletKeyPair.pubKey.toHexKey()

            val event =
                LnZapPaymentRequestEvent.create(
                    lnInvoice = "lnbc50n1...",
                    walletServicePubkey = walletServicePubkey,
                    signer = clientSigner,
                )

            // Wallet service should be able to decrypt
            val decrypted = event.decryptRequest(walletSigner)
            assertIs<PayInvoiceMethod>(decrypted)
            assertEquals("lnbc50n1...", decrypted.params?.invoice)
        }

    @Test
    fun testDecryptGenericRequest() =
        runTest {
            val clientKeyPair = KeyPair()
            val walletKeyPair = KeyPair()
            val clientSigner = NostrSignerInternal(clientKeyPair)
            val walletSigner = NostrSignerInternal(walletKeyPair)
            val walletServicePubkey: HexKey =
                walletKeyPair.pubKey.toHexKey()

            val request = MakeInvoiceMethod.create(5000L, "test payment")
            val event =
                LnZapPaymentRequestEvent.createRequest(
                    request = request,
                    walletServicePubkey = walletServicePubkey,
                    signer = clientSigner,
                )

            val decrypted = event.decryptRequest(walletSigner)
            assertIs<MakeInvoiceMethod>(decrypted)
            assertEquals(5000L, decrypted.params?.amount)
            assertEquals("test payment", decrypted.params?.description)
        }

    @Test
    fun testCanDecrypt() =
        runTest {
            val clientKeyPair = KeyPair()
            val walletKeyPair = KeyPair()
            val otherKeyPair = KeyPair()
            val clientSigner = NostrSignerInternal(clientKeyPair)
            val walletSigner = NostrSignerInternal(walletKeyPair)
            val otherSigner = NostrSignerInternal(otherKeyPair)
            val walletServicePubkey: HexKey =
                walletKeyPair.pubKey.toHexKey()

            val event =
                LnZapPaymentRequestEvent.create(
                    lnInvoice = "lnbc50n1...",
                    walletServicePubkey = walletServicePubkey,
                    signer = clientSigner,
                )

            assertTrue(event.canDecrypt(clientSigner))
            assertTrue(event.canDecrypt(walletSigner))
            assertTrue(!event.canDecrypt(otherSigner))
        }
}
