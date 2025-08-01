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
package com.vitorpamplona.quartz.nip57Zaps

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.caches.DecryptCache

class PrivateZapCache(
    signer: NostrSigner,
) {
    private val decryptionCache =
        object : LruCache<LnZapRequestEvent, PrivateZapDecryptCache>(1000) {
            override fun create(key: LnZapRequestEvent): PrivateZapDecryptCache? {
                val zappedAuthor = key.zappedAuthor().firstOrNull()
                return if (key.isPrivateZap() && zappedAuthor != null) {
                    PrivateZapDecryptCache(signer)
                } else {
                    null
                }
            }
        }

    fun delete(event: LnZapRequestEvent) {
        decryptionCache.remove(event)
    }

    fun cachedPrivateZap(event: LnZapRequestEvent): LnZapPrivateEvent? = decryptionCache[event]?.cached()

    suspend fun decryptPrivateZap(event: LnZapRequestEvent) = decryptionCache[event]?.decrypt(event)
}

class PrivateZapDecryptCache(
    signer: NostrSigner,
) : DecryptCache<LnZapRequestEvent, LnZapPrivateEvent>(signer) {
    override suspend fun decryptAndParse(
        event: LnZapRequestEvent,
        signer: NostrSigner,
    ): LnZapPrivateEvent = signer.decryptZapEvent(event)
}
