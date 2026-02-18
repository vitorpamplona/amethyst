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
package com.vitorpamplona.quartz.nip57Zaps

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class LnZapRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun zappedPost() = tags.mapNotNull(ETag::parseId)

    fun zappedAuthor() = tags.mapNotNull(PTag::parseKey)

    fun isPrivateZap() = tags.any { t -> t.size >= 2 && t[0] == "anon" && t[1].isNotBlank() }

    fun getAnonTag(): String {
        val anonTag = tags.firstOrNull { t -> t.size >= 2 && t[0] == "anon" }
        if (anonTag != null) {
            val encNote = anonTag[1]
            if (encNote.isNotBlank()) {
                return encNote
            } else {
                throw IllegalStateException("Anon tag is empty.")
            }
        } else {
            throw IllegalStateException("This is not a private zap.")
        }
    }

    companion object {
        const val KIND = 9734
        const val ALT = "Zap request"

        suspend fun create(
            zappedEvent: Event,
            relays: Set<NormalizedRelayUrl>,
            signer: NostrSigner,
            pollOption: Int?,
            message: String,
            zapType: LnZapEvent.ZapType,
            toUserPubHex: String?,
            createdAt: Long = TimeUtils.now(),
        ): LnZapRequestEvent {
            var tags =
                listOf(
                    arrayOf("e", zappedEvent.id),
                    arrayOf("p", toUserPubHex ?: zappedEvent.pubKey),
                    arrayOf("relays") + relays.map { it.url },
                    AltTag.assemble(ALT),
                )
            if (zappedEvent is AddressableEvent) {
                tags = tags + listOf(ATag.assemble(zappedEvent.address(), null))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(arrayOf(PollOptionTag.TAG_NAME, pollOption.toString()))
            }

            return when (zapType) {
                LnZapEvent.ZapType.PUBLIC -> {
                    signer.sign(createdAt, KIND, tags.toTypedArray(), message)
                }

                LnZapEvent.ZapType.ANONYMOUS -> {
                    tags = tags + listOf(arrayOf("anon"))
                    NostrSignerInternal(KeyPair()).sign(createdAt, KIND, tags.toTypedArray(), message)
                }

                LnZapEvent.ZapType.PRIVATE -> {
                    tags = tags + listOf(arrayOf("anon", ""))
                    signer.sign(createdAt, KIND, tags.toTypedArray(), message)
                }

                LnZapEvent.ZapType.NONZAP -> {
                    throw IllegalArgumentException("Invalid zap type")
                }
            }
        }

        suspend fun create(
            userHex: String,
            relays: Set<NormalizedRelayUrl>,
            signer: NostrSigner,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = TimeUtils.now(),
        ): LnZapRequestEvent {
            var tags =
                arrayOf(
                    arrayOf("p", userHex),
                    arrayOf("relays") + relays.map { it.url },
                )

            return when (zapType) {
                LnZapEvent.ZapType.PUBLIC -> {
                    signer.sign(createdAt, KIND, tags, message)
                }

                LnZapEvent.ZapType.ANONYMOUS -> {
                    tags += arrayOf(arrayOf("anon", ""))
                    NostrSignerInternal(KeyPair()).sign(createdAt, KIND, tags, message)
                }

                LnZapEvent.ZapType.PRIVATE -> {
                    tags += arrayOf(arrayOf("anon", ""))
                    signer.sign(createdAt, KIND, tags, message)
                }

                LnZapEvent.ZapType.NONZAP -> {
                    throw IllegalArgumentException("Invalid zap type")
                }
            }
        }
    }
}
