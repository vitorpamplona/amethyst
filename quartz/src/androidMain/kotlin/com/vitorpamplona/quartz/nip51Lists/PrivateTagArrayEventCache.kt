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
package com.vitorpamplona.quartz.nip51Lists

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.caches.DecryptCache
import kotlin.collections.plus

class PrivateTagArrayEventCache<T : PrivateTagArrayEvent>(
    signer: NostrSigner,
    cacheSize: Int = 10,
) {
    private val decryptionCache =
        object : LruCache<T, PrivateTagArrayEventDecryptCache<T>>(cacheSize) {
            override fun create(key: T): PrivateTagArrayEventDecryptCache<T>? =
                if (key.content.isNotBlank() && key.pubKey == signer.pubKey) {
                    PrivateTagArrayEventDecryptCache<T>(signer)
                } else {
                    null
                }
        }

    fun remove(event: T) = decryptionCache.remove(event)

    fun cachedPrivateTags(event: T): TagArray? = decryptionCache[event]?.cached()

    suspend fun privateTags(event: T) = decryptionCache[event]?.decrypt(event)

    suspend fun mergeTagList(event: T): TagArray = event.tags + (privateTags(event) ?: emptyArray())

    fun mergeTagListPrecached(event: T): TagArray = event.tags + (cachedPrivateTags(event) ?: emptyArray())
}

class PrivateTagArrayEventDecryptCache<T : PrivateTagArrayEvent>(
    signer: NostrSigner,
) : DecryptCache<T, TagArray>(signer) {
    override suspend fun decryptAndParse(
        event: T,
        signer: NostrSigner,
    ): TagArray = event.decrypt(signer)
}
