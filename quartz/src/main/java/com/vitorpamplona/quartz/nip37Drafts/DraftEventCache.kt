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
package com.vitorpamplona.quartz.nip37Drafts

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.caches.DecryptCache

class DraftEventCache(
    signer: NostrSigner,
) {
    private val decryptionCache =
        object : LruCache<DraftEvent, DraftEventDecryptCache>(1000) {
            override fun create(key: DraftEvent): DraftEventDecryptCache? =
                if (!key.isDeleted() && key.pubKey == signer.pubKey) {
                    DraftEventDecryptCache(signer)
                } else {
                    null
                }
        }

    fun delete(event: DraftEvent) = decryptionCache.remove(event)

    fun preload(
        event: DraftEvent,
        result: Event,
    ) = decryptionCache[event]?.preload(result)

    fun preCachedDraft(event: DraftEvent): Event? = decryptionCache[event]?.cached()

    suspend fun cachedDraft(event: DraftEvent) = decryptionCache[event]?.decrypt(event)
}

class DraftEventDecryptCache(
    signer: NostrSigner,
) : DecryptCache<DraftEvent, Event>(signer) {
    override suspend fun decryptAndParse(
        event: DraftEvent,
        signer: NostrSigner,
    ): Event = event.decryptInnerEvent(signer)
}
