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
package com.vitorpamplona.amethyst.desktop.nwc

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47Client
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for NWC RPC round-trips using real crypto.
 * No mocking — tests the full encrypt/decrypt cycle with deterministic keys.
 */
class NwcRpcIntegrationTest {
    private val clientKeyPair = KeyPair()
    private val walletKeyPair = KeyPair()
    private val clientSigner = NostrSignerInternal(clientKeyPair)
    private val walletSigner = NostrSignerInternal(walletKeyPair)
    private val testRelay = NormalizedRelayUrl("wss://relay.test/")

    private fun createClient() =
        Nip47Client(
            walletPubKeyHex = walletKeyPair.pubKey.toHexKey(),
            relayUrl = testRelay,
            signer = clientSigner,
        )

    @Test
    fun `pay_invoice request round-trip`() =
        runTest {
            val client = createClient()
            val requestEvent = client.payInvoice("lnbc50n1pjtest...")

            // Verify request event structure
            assertEquals(LnZapPaymentRequestEvent.KIND, requestEvent.kind)
            assertTrue(requestEvent.content.isNotBlank())

            // Wallet decrypts the request
            val decrypted = requestEvent.decryptRequest(walletSigner)
            assertIs<PayInvoiceMethod>(decrypted)
            assertEquals("lnbc50n1pjtest...", decrypted.params?.invoice)
        }

    @Test
    fun `get_balance full round-trip`() =
        runTest {
            val client = createClient()
            val requestEvent = client.getBalance()

            // Verify it's a valid NWC request
            assertEquals(LnZapPaymentRequestEvent.KIND, requestEvent.kind)

            // Wallet decrypts and verifies method type
            val decrypted = requestEvent.decryptRequest(walletSigner)
            assertIs<GetBalanceMethod>(decrypted)

            // Wallet builds encrypted response
            val responseEvent =
                LnZapPaymentResponseEvent.createResponse(
                    response =
                        GetBalanceSuccessResponse(
                            result = GetBalanceSuccessResponse.GetBalanceResult(balance = 125_000),
                        ),
                    requestEvent = requestEvent,
                    signer = walletSigner,
                )

            // Verify response event structure
            assertEquals(LnZapPaymentResponseEvent.KIND, responseEvent.kind)
            assertEquals(requestEvent.id, responseEvent.requestId())

            // Client decrypts response
            val response = responseEvent.decrypt(clientSigner)
            assertIs<GetBalanceSuccessResponse>(response)
            assertEquals(125_000, response.result?.balance)
        }

    @Test
    fun `make_invoice full round-trip`() =
        runTest {
            val client = createClient()
            val requestEvent = client.makeInvoice(amount = 50_000, description = "Test invoice")

            // Wallet decrypts
            val decrypted = requestEvent.decryptRequest(walletSigner)
            assertIs<MakeInvoiceMethod>(decrypted)
            assertEquals(50_000, decrypted.params?.amount)
            assertEquals("Test invoice", decrypted.params?.description)

            // Wallet responds with invoice
            val fakeInvoice = "lnbc500n1pjgenerated..."
            val responseEvent =
                LnZapPaymentResponseEvent.createResponse(
                    response =
                        MakeInvoiceSuccessResponse(
                            result =
                                NwcTransaction(
                                    invoice = fakeInvoice,
                                    payment_hash = "abc123hash",
                                    amount = 50_000,
                                ),
                        ),
                    requestEvent = requestEvent,
                    signer = walletSigner,
                )

            // Client decrypts
            val response = responseEvent.decrypt(clientSigner)
            assertIs<MakeInvoiceSuccessResponse>(response)
            assertNotNull(response.result)
            assertEquals(fakeInvoice, response.result?.invoice)
            assertEquals("abc123hash", response.result?.payment_hash)
        }

    @Test
    fun `pay_invoice success response round-trip`() =
        runTest {
            val client = createClient()
            val requestEvent = client.payInvoice("lnbc100n1...")

            val responseEvent =
                LnZapPaymentResponseEvent.createResponse(
                    response =
                        PayInvoiceSuccessResponse(
                            result =
                                PayInvoiceSuccessResponse.PayInvoiceResultParams(
                                    preimage = "deadbeef0123456789",
                                ),
                        ),
                    requestEvent = requestEvent,
                    signer = walletSigner,
                )

            val response = responseEvent.decrypt(clientSigner)
            assertIs<PayInvoiceSuccessResponse>(response)
            assertEquals("deadbeef0123456789", response.result?.preimage)
        }

    @Test
    fun `NWC URI creates valid client`() =
        runTest {
            val nwcUri =
                "nostr+walletconnect://${walletKeyPair.pubKey.toHexKey()}" +
                    "?relay=wss%3A%2F%2Frelay.test%2F" +
                    "&secret=${clientKeyPair.privKey!!.toHexKey()}"

            val client = Nip47Client.fromUri(nwcUri)
            assertEquals(walletKeyPair.pubKey.toHexKey(), client.walletPubKeyHex)

            // Build a request and verify it works
            val requestEvent = client.getBalance()
            assertEquals(LnZapPaymentRequestEvent.KIND, requestEvent.kind)

            // Wallet can decrypt it
            val decrypted = requestEvent.decryptRequest(walletSigner)
            assertIs<GetBalanceMethod>(decrypted)
        }
}
