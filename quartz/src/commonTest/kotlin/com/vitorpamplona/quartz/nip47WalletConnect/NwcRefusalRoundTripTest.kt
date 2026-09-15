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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.cache.NostrWalletConnectResponseCache
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A wallet's refusal, encrypted exactly as it sent it, decrypts through the response
 * cache the zap path reads into something with a message to render — whatever the error
 * code, over NIP-04 and NIP-44 alike. Parsing alone is pinned in [ResponseTest]; this
 * covers the wire bytes. It does not cover `LocalCache.consume` or relay delivery.
 */
class NwcRefusalRoundTripTest {
    private val clientSigner = NostrSignerInternal(KeyPair())
    private val walletSigner = NostrSignerInternal(KeyPair())

    private suspend fun assertRefusalDecryptsWithMessage(
        code: String,
        message: String,
    ) {
        val walletJson = """{"result_type":"pay_invoice","error":{"code":"$code","message":"$message"}}"""

        for (useNip44 in listOf(false, true)) {
            val request =
                LnZapPaymentRequestEvent.createRequest(
                    PayInvoiceMethod.create("lnbc50n1pjtest"),
                    walletSigner.pubKey,
                    clientSigner,
                    useNip44 = useNip44,
                )

            // Encrypted by hand rather than through LnZapPaymentResponseEvent.createResponse,
            // which re-serializes a Response: the point is the wallet's bytes, verbatim.
            val encrypted =
                if (useNip44) {
                    walletSigner.nip44Encrypt(walletJson, clientSigner.pubKey)
                } else {
                    walletSigner.nip04Encrypt(walletJson, clientSigner.pubKey)
                }
            val reply =
                walletSigner.sign<LnZapPaymentResponseEvent>(
                    request.createdAt,
                    LnZapPaymentResponseEvent.KIND,
                    arrayOf(arrayOf("p", clientSigner.pubKey), arrayOf("e", request.id)),
                    encrypted,
                )

            val response = NostrWalletConnectResponseCache(clientSigner).decryptResponse(reply)
            val case = "$code over ${if (useNip44) "NIP-44" else "NIP-04"}"
            assertIs<IErrorResponseLike>(response, case)
            assertEquals(message, response.errorMessage(), case)
        }
    }

    @Test
    fun quotaExceededDecryptsWithItsMessage() = runTest { assertRefusalDecryptsWithMessage("QUOTA_EXCEEDED", "this payment would exceed the connection's budget for this period") }

    @Test
    fun paymentFailedDecryptsWithItsMessage() = runTest { assertRefusalDecryptsWithMessage("PAYMENT_FAILED", "NO_ROUTE: no route found") }
}
