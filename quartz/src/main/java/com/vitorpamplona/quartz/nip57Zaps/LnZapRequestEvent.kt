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
package com.vitorpamplona.quartz.nip57Zaps

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.mapValues
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class LnZapRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient private var privateZapEvent: LnZapPrivateEvent? = null

    override fun countMemory(): Long = super.countMemory() + pointerSizeInBytes + (privateZapEvent?.countMemory() ?: 0)

    fun zappedPost() = tags.mapValues("e")

    fun zappedAuthor() = tags.mapValues("p")

    fun isPrivateZap() = tags.any { t -> t.size >= 2 && t[0] == "anon" && t[1].isNotBlank() }

    fun getPrivateZapEvent(
        loggedInUserPrivKey: ByteArray,
        pubKey: HexKey,
    ): LnZapPrivateEvent? {
        val anonTag = tags.firstOrNull { t -> t.size >= 2 && t[0] == "anon" }
        if (anonTag != null) {
            val encnote = anonTag[1]
            if (encnote.isNotBlank()) {
                try {
                    val note = PrivateZapEncryption.decryptPrivateZapMessage(encnote, loggedInUserPrivKey, pubKey.hexToByteArray())
                    val decryptedEvent = fromJson(note)
                    if (decryptedEvent.kind == 9733) {
                        return decryptedEvent as LnZapPrivateEvent
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return null
    }

    fun cachedPrivateZap(): LnZapPrivateEvent? = privateZapEvent

    fun decryptPrivateZap(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        privateZapEvent?.let {
            onReady(it)
            return
        }

        signer.decryptZapEvent(this) {
            // caches it
            privateZapEvent = it
            onReady(it)
        }
    }

    companion object {
        const val KIND = 9734
        const val ALT = "Zap request"

        fun create(
            originalNote: Event,
            relays: Set<String>,
            signer: NostrSigner,
            pollOption: Int?,
            message: String,
            zapType: LnZapEvent.ZapType,
            toUserPubHex: String?,
            createdAt: Long = TimeUtils.now(),
            onReady: (LnZapRequestEvent) -> Unit,
        ) {
            if (zapType == LnZapEvent.ZapType.NONZAP) return

            var tags =
                listOf(
                    arrayOf("e", originalNote.id),
                    arrayOf("p", toUserPubHex ?: originalNote.pubKey),
                    arrayOf("relays") + relays,
                    AltTag.assemble(ALT),
                )
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(arrayOf("a", originalNote.aTag().toTag()))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(arrayOf(PollOptionTag.TAG_NAME, pollOption.toString()))
            }

            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                tags = tags + listOf(arrayOf("anon"))
                NostrSignerInternal(KeyPair()).sign(createdAt, KIND, tags.toTypedArray(), message, onReady)
            } else if (zapType == LnZapEvent.ZapType.PRIVATE) {
                tags = tags + listOf(arrayOf("anon", ""))
                signer.sign(createdAt, KIND, tags.toTypedArray(), message, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), message, onReady)
            }
        }

        fun create(
            userHex: String,
            relays: Set<String>,
            signer: NostrSigner,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = TimeUtils.now(),
            onReady: (LnZapRequestEvent) -> Unit,
        ) {
            if (zapType == LnZapEvent.ZapType.NONZAP) return

            var tags =
                arrayOf(
                    arrayOf("p", userHex),
                    arrayOf("relays") + relays,
                )

            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                tags += arrayOf(arrayOf("anon", ""))
                NostrSignerInternal(KeyPair()).sign(createdAt, KIND, tags, message, onReady)
            } else if (zapType == LnZapEvent.ZapType.PRIVATE) {
                tags += arrayOf(arrayOf("anon", ""))
                signer.sign(createdAt, KIND, tags, message, onReady)
            } else {
                signer.sign(createdAt, KIND, tags, message, onReady)
            }
        }
    }
}
