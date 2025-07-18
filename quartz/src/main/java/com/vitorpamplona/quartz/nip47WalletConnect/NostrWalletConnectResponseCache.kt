/**
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

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.caches.DecryptCache

class NostrWalletConnectResponseCache(
    signer: NostrSigner,
) {
    private val decryptionCache =
        object : LruCache<LnZapPaymentResponseEvent, NWCResponseDecryptCache>(50) {
            override fun create(key: LnZapPaymentResponseEvent): NWCResponseDecryptCache? =
                if (key.content.isNotBlank() && key.canDecrypt(signer)) {
                    NWCResponseDecryptCache(signer)
                } else {
                    null
                }
        }

    fun cachedResponse(event: LnZapPaymentResponseEvent): Response? = decryptionCache[event]?.cached()

    suspend fun decryptResponse(event: LnZapPaymentResponseEvent) = decryptionCache[event]?.decrypt(event)
}

class NWCResponseDecryptCache(
    signer: NostrSigner,
) : DecryptCache<LnZapPaymentResponseEvent, Response>(signer) {
    override suspend fun decryptAndParse(
        event: LnZapPaymentResponseEvent,
        signer: NostrSigner,
    ) = event.decrypt(signer)
}
