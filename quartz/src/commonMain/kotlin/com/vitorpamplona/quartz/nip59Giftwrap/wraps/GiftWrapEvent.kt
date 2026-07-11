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
package com.vitorpamplona.quartz.nip59Giftwrap.wraps

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip59Giftwrap.HasInnerEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Caller hook to adjust the finished wrap template right before the ephemeral
 * key signs it — e.g. mining a NIP-13 proof of work into it. Receives the
 * ephemeral key's pubkey because the NIP-01 id (what a nonce commits to)
 * includes it, and the key never leaves [GiftWrapEvent.create]. Must return a
 * template of the same kind; the default is the identity.
 */
typealias GiftWrapTemplateConversion = (template: EventTemplate<GiftWrapEvent>, ephemeralPubKey: HexKey) -> EventTemplate<GiftWrapEvent>

@Immutable
open class GiftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
    kind: Int = KIND,
) : Event(id, pubKey, createdAt, kind, tags, content, sig),
    HasInnerEvent {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    override var innerEventId: HexKey? = null

    open fun copyNoContent(): GiftWrapEvent {
        val copy =
            GiftWrapEvent(
                id,
                pubKey,
                createdAt,
                tags,
                "",
                sig,
            )

        copy.innerEventId = innerEventId

        return copy
    }

    override fun isContentEncoded() = true

    suspend fun unwrapThrowing(signer: NostrSigner): Event {
        val giftStr = plainContent(signer)
        val gift = fromJson(giftStr)

        innerEventId = gift.id

        return gift
    }

    suspend fun unwrapOrNull(signer: NostrSigner): Event? =
        try {
            unwrapThrowing(signer)
        } catch (_: Exception) {
            Log.d("GiftWrapEvent") { "Couldn't Decrypt the content " + this.toNostrUri() }
            null
        }

    private suspend fun plainContent(signer: NostrSigner): String {
        if (content.isEmpty()) return ""

        return signer.nip44Decrypt(content, pubKey)
    }

    fun recipientPubKey() = tags.firstTagValue("p")

    companion object {
        const val KIND = 1059
        const val ALT = "Encrypted event"

        /**
         * Build a NIP-59 gift wrap addressed to `recipientPubKey`.
         *
         * Per NIP-17 §Publishing, the `p` tag on the wrap MAY carry the
         * recipient's primary DM inbox relay as a hint, so other clients
         * the recipient runs (or relays acting as inbox routers) can locate
         * the wrap without a separate kind:10050 lookup. Pass it via
         * [recipientRelayHint] — `null` (the default) preserves the
         * historical 2-element `["p", pubkey]` shape.
         *
         * [templateConversion] runs on the finished template right before the
         * ephemeral key signs it. This is how a caller mines a NIP-13 proof
         * of work into the wrap itself (the ephemeral-key envelope, never the
         * inner seal or rumor) so DM relays can PoW-filter inbox spam —
         * without this NIP-59 code knowing anything about mining.
         */
        fun create(
            event: Event,
            recipientPubKey: HexKey,
            expirationDelta: Long? = null,
            createdAt: Long = TimeUtils.randomWithTwoDays(),
            recipientRelayHint: NormalizedRelayUrl? = null,
            templateConversion: GiftWrapTemplateConversion = { template, _ -> template },
        ): GiftWrapEvent {
            val signer = NostrSignerSync(KeyPair()) // GiftWrap is always a random key

            val tags =
                expirationDelta?.let {
                    // minimum expiration is two days in the future due to the random created at
                    // this will make sure the even arrives and is not deleted because of the 2 days.
                    arrayOf(
                        PTag.assemble(recipientPubKey, recipientRelayHint),
                        ExpirationTag.assemble(createdAt + it + TimeUtils.twoDays()),
                    )
                } ?: arrayOf(
                    PTag.assemble(recipientPubKey, recipientRelayHint),
                )

            val template =
                EventTemplate<GiftWrapEvent>(
                    createdAt = createdAt,
                    kind = KIND,
                    tags = tags,
                    content = signer.nip44Encrypt(event.toJson(), recipientPubKey),
                )

            val readyToSign = templateConversion(template, signer.pubKey)

            return signer.sign(
                createdAt = readyToSign.createdAt,
                kind = readyToSign.kind,
                tags = readyToSign.tags,
                content = readyToSign.content,
            )
        }
    }
}
