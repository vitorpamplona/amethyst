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

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
abstract class PrivateTagArrayEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient private var privateTagsCache: Array<Array<String>>? = null

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (privateTagsCache?.sumOf { pointerSizeInBytes + it.sumOf { pointerSizeInBytes + it.bytesUsedInMemory() } } ?: 0)

    override fun isContentEncoded() = true

    fun cachedPrivateTags(): Array<Array<String>>? = privateTagsCache

    fun privateTags(
        signer: NostrSigner,
        onReady: (Array<Array<String>>) -> Unit,
    ) {
        if (content.isEmpty()) {
            onReady(emptyArray())
            return
        }

        privateTagsCache?.let {
            onReady(it)
            return
        }

        try {
            PrivateTagsInContent.decrypt(content, signer) {
                privateTagsCache = it
                privateTagsCache?.let { onReady(it) }
            }
        } catch (e: Throwable) {
            onReady(emptyArray())
            Log.w("GeneralList", "Error parsing the JSON ${e.message}")
        }
    }

    fun mergeTagList(
        signer: NostrSigner,
        onReady: (Array<Array<String>>) -> Unit,
    ) {
        privateTags(signer) {
            onReady(tags + it)
        }
    }

    fun <T> mapAllTags(
        privateTags: Array<Array<String>>,
        mapper: (Array<String>) -> T,
    ): Set<T> {
        val privateRooms = privateTags.mapNotNull(mapper)
        val publicRooms = tags.mapNotNull(mapper)

        return (privateRooms + publicRooms).toSet()
    }
}
