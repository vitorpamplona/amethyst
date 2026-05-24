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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

/**
 * NIP-57 zap-request building + LN address extraction.
 *
 * Returns a signed [LnZapRequestEvent] (kind:9734) — the artifact a caller
 * hands to a LNURL-pay callback to receive a BOLT11 invoice. The Lightning
 * round-trip (LNURL fetch, invoice retrieval, optional NWC payment) is
 * intentionally out of scope here; callers compose this with
 * `LightningAddressResolver` (commons/jvmAndroid) or their own LN client.
 *
 * Pattern matches [FollowActions] and [SearchActions]: shared, pure logic
 * usable from amy CLI, the future Android App Functions adapter for Gemini,
 * and any other non-UI consumer.
 */
object ZapActions {
    /** Convert sats to millisats — LN-side amount unit. */
    fun satsToMillisats(sats: Long): Long = sats * 1000L

    /**
     * Extract the LN address (Lightning Address or LNURL) from a kind:0
     * metadata event. Prefers `lud16` (Lightning Address, `user@domain`)
     * over `lud06` (raw LNURL). Returns null when the user has no LN
     * details published.
     */
    fun extractLnAddress(metadata: MetadataEvent): String? = metadata.contactMetaData()?.lnAddress()

    /**
     * Build a NIP-57 profile zap request — pays [recipientPubkey] directly,
     * not attached to any specific event.
     *
     * [inboxRelays] becomes the `["relays", ...]` tag of the zap request:
     * the LN provider publishes the kind:9735 zap *receipt* to these
     * relays. These should be the sender's read-side (NIP-65 inbox)
     * relays so the sender's clients see the receipt land.
     *
     * Pass [lnurl] when known to stamp it as a tag on the request — some
     * receipt validators key off it.
     */
    suspend fun buildUserZapRequest(
        signer: NostrSigner,
        recipientPubkey: HexKey,
        amountMillisats: Long,
        inboxRelays: Set<NormalizedRelayUrl>,
        comment: String = "",
        zapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PUBLIC,
        lnurl: String? = null,
    ): LnZapRequestEvent =
        LnZapRequestEvent.create(
            userHex = recipientPubkey,
            relays = inboxRelays,
            signer = signer,
            message = comment,
            zapType = zapType,
            amountMillisats = amountMillisats,
            lnurl = lnurl,
        )

    /**
     * Build a NIP-57 event-zap request — pays the author of
     * [zappedEvent] in the context of that specific event. Override
     * [toUserPubkey] when the payment should go to a co-author or
     * delegated recipient (zap splits); when null the zap targets
     * `zappedEvent.pubKey`.
     */
    suspend fun buildEventZapRequest(
        signer: NostrSigner,
        zappedEvent: Event,
        amountMillisats: Long,
        inboxRelays: Set<NormalizedRelayUrl>,
        comment: String = "",
        zapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PUBLIC,
        toUserPubkey: HexKey? = null,
        pollOption: Int? = null,
        lnurl: String? = null,
    ): LnZapRequestEvent =
        LnZapRequestEvent.create(
            zappedEvent = zappedEvent,
            relays = inboxRelays,
            signer = signer,
            pollOption = pollOption,
            message = comment,
            zapType = zapType,
            toUserPubHex = toUserPubkey,
            amountMillisats = amountMillisats,
            lnurl = lnurl,
        )
}
