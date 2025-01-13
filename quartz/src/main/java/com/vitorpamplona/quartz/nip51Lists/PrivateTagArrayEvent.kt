/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
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
            signer.decrypt(content, pubKey) {
                privateTagsCache = EventMapper.mapper.readValue<Array<Array<String>>>(it)
                privateTagsCache?.let { onReady(it) }
            }
        } catch (e: Throwable) {
            Log.w("GeneralList", "Error parsing the JSON ${e.message}")
        }
    }

    fun decryptChangeEncrypt(
        signer: NostrSigner,
        change: (Array<Array<String>>) -> Array<Array<String>>,
        onReady: (content: String) -> Unit,
    ) {
        privateTags(signer) { privateTags ->
            encryptTags(
                privateTags = change(privateTags),
                signer = signer,
            ) { encryptedTags ->
                onReady(encryptedTags)
            }
        }
    }

    companion object {
        fun add(
            current: PrivateTagArrayEvent,
            newTag: Array<String>,
            toPrivate: Boolean,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (toPrivate) {
                current.privateTags(signer) { privateTags ->
                    encryptTags(
                        privateTags = privateTags.plus(newTag),
                        signer = signer,
                    ) { encryptedTags ->
                        onReady(encryptedTags, current.tags)
                    }
                }
            } else {
                onReady(current.content, current.tags.plus(newTag))
            }
        }

        fun addAll(
            current: PrivateTagArrayEvent,
            newTag: Array<Array<String>>,
            toPrivate: Boolean,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (toPrivate) {
                current.privateTags(signer) { privateTags ->
                    encryptTags(
                        privateTags = privateTags.plus(newTag),
                        signer = signer,
                    ) { encryptedTags ->
                        onReady(encryptedTags, current.tags)
                    }
                }
            } else {
                onReady(current.content, current.tags.plus(newTag))
            }
        }

        fun replaceAllToPrivateNewTag(
            dTag: String,
            current: PrivateTagArrayEvent?,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (current == null) {
                createPrivate(dTag, newTag, signer, onReady)
            } else {
                replaceAllToPrivateNewTag(current, oldTagStartsWith, newTag, signer, onReady)
            }
        }

        fun replaceAllToPublicNewTag(
            dTag: String,
            current: PrivateTagArrayEvent?,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (current == null) {
                createPublic(dTag, newTag, signer, onReady)
            } else {
                replaceAllToPublicNewTag(current, oldTagStartsWith, newTag, signer, onReady)
            }
        }

        fun replaceAllToPrivateNewTag(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                encryptTags(
                    privateTags = privateTags.replaceAll(oldTagStartsWith, newTag),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags.remove(oldTagStartsWith))
                }
            }
        }

        fun replaceAllToPublicNewTag(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                encryptTags(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags.remove(oldTagStartsWith).plus(newTag))
                }
            }
        }

        fun removeAllFromPrivate(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                encryptTags(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags)
                }
            }
        }

        fun removeAllFromPublic(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) = onReady(current.content, current.tags.remove(oldTagStartsWith))

        fun removeAll(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                encryptTags(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags.remove(oldTagStartsWith))
                }
            }
        }

        fun createPrivate(
            dTag: String,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            encryptTags(
                privateTags = arrayOf(newTag),
                signer = signer,
            ) { encryptedTags ->
                onReady(encryptedTags, arrayOf(arrayOf("d", dTag)))
            }
        }

        fun createPublic(
            dTag: String,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            onReady("", arrayOf(arrayOf("d", dTag), newTag))
        }

        fun encryptTags(
            privateTags: Array<Array<String>>? = null,
            signer: NostrSigner,
            onReady: (String) -> Unit,
        ) = signer.nip04Encrypt(
            if (privateTags.isNullOrEmpty()) "" else EventMapper.mapper.writeValueAsString(privateTags),
            signer.pubKey,
            onReady,
        )
    }
}
