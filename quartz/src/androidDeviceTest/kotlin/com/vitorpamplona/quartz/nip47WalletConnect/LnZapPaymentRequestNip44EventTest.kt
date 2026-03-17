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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class LnZapPaymentRequestNip44EventTest {
    @Test
    fun testCreateRequestWithNip44() =
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
                    useNip44 = true,
                )

            assertEquals(23194, event.kind)
            assertEquals("nip44_v2", event.encryptionScheme())
        }

    @Test
    fun testDecryptNip44Request() =
        runTest {
            val clientKeyPair = KeyPair()
            val walletKeyPair = KeyPair()
            val clientSigner = NostrSignerInternal(clientKeyPair)
            val walletSigner = NostrSignerInternal(walletKeyPair)
            val walletServicePubkey: HexKey =
                walletKeyPair.pubKey.toHexKey()

            val request = GetInfoMethod.create()
            val event =
                LnZapPaymentRequestEvent.createRequest(
                    request = request,
                    walletServicePubkey = walletServicePubkey,
                    signer = clientSigner,
                    useNip44 = true,
                )

            assertEquals("nip44_v2", event.encryptionScheme())

            // Wallet service should be able to decrypt NIP-44 encrypted request
            val decrypted = event.decryptRequest(walletSigner)
            assertIs<GetInfoMethod>(decrypted)
        }
}
