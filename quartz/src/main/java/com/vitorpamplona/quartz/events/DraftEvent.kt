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
package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DraftEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    override fun isContentEncoded() = true

    fun isDeleted() = content == ""

    fun preCachedDraft(signer: NostrSigner): Event? {
        return cachedInnerEvent[signer.pubKey]
    }

    fun preCachedDraft(pubKey: HexKey): Event? {
        return cachedInnerEvent[pubKey]
    }

    fun allCache() = cachedInnerEvent.values

    fun addToCache(
        pubKey: HexKey,
        innerEvent: Event,
    ) {
        cachedInnerEvent = cachedInnerEvent + Pair(pubKey, innerEvent)
    }

    fun cachedDraft(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        cachedInnerEvent[signer.pubKey]?.let {
            onReady(it)
            return
        }
        decrypt(signer) { draft ->
            addToCache(signer.pubKey, draft)

            onReady(draft)
        }
    }

    private fun decrypt(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        try {
            plainContent(signer) {
                try {
                    onReady(fromJson(it))
                } catch (e: Exception) {
                    // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
                }
            }
        } catch (e: Exception) {
            // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
        }
    }

    private fun plainContent(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        if (content.isEmpty()) return

        signer.nip44Decrypt(content, pubKey, onReady)
    }

    fun createDeletedEvent(
        signer: NostrSigner,
        onReady: (DraftEvent) -> Unit,
    ) {
        signer.sign<DraftEvent>(createdAt, KIND, tags, "") {
            onReady(it)
        }
    }

    companion object {
        const val KIND = 31234

        fun createAddressTag(
            pubKey: HexKey,
            dTag: String,
        ): String {
            return ATag.assembleATag(KIND, pubKey, dTag)
        }

        fun create(
            dTag: String,
            originalNote: LiveActivitiesChatMessageEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DraftEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            originalNote.activity()?.let { tags.add(arrayOf("a", it.toTag())) }
            originalNote.replyingTo()?.let { tags.add(arrayOf("e", it)) }

            create(dTag, originalNote, emptyList(), signer, createdAt, onReady)
        }

        fun create(
            dTag: String,
            originalNote: ChannelMessageEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DraftEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            originalNote.channel()?.let { tags.add(arrayOf("e", it)) }

            create(dTag, originalNote, tags, signer, createdAt, onReady)
        }

        fun create(
            dTag: String,
            originalNote: GitReplyEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DraftEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            originalNote.repository()?.let { tags.add(arrayOf("a", it.toTag())) }
            originalNote.replyingTo()?.let { tags.add(arrayOf("e", it)) }

            create(dTag, originalNote, tags, signer, createdAt, onReady)
        }

        fun create(
            dTag: String,
            originalNote: PollNoteEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DraftEvent) -> Unit,
        ) {
            val tagsWithMarkers =
                originalNote.tags().filter {
                    it.size > 3 && (it[0] == "e" || it[0] == "a") && (it[3] == "root" || it[3] == "reply")
                }

            create(dTag, originalNote, tagsWithMarkers, signer, createdAt, onReady)
        }

        fun create(
            dTag: String,
            originalNote: TextNoteEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DraftEvent) -> Unit,
        ) {
            val tagsWithMarkers =
                originalNote.tags().filter {
                    it.size > 3 && (it[0] == "e" || it[0] == "a") && (it[3] == "root" || it[3] == "reply")
                }

            create(dTag, originalNote, tagsWithMarkers, signer, createdAt, onReady)
        }

        fun create(
            dTag: String,
            innerEvent: Event,
            anchorTagArray: List<Array<String>> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DraftEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("d", dTag))
            tags.add(arrayOf("k", "${innerEvent.kind}"))

            if (anchorTagArray.isNotEmpty()) {
                tags.addAll(anchorTagArray)
            }

            signer.nip44Encrypt(innerEvent.toJson(), signer.pubKey) { encryptedContent ->
                signer.sign<DraftEvent>(createdAt, KIND, tags.toTypedArray(), encryptedContent) {
                    it.addToCache(signer.pubKey, innerEvent)
                    onReady(it)
                }
            }
        }
    }
}
