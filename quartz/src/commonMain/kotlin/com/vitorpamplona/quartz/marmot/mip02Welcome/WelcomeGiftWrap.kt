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
package com.vitorpamplona.quartz.marmot.mip02Welcome

import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeGiftWrap.wrapForRecipient
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Composes the NIP-59 gift wrap pipeline for Marmot Welcome messages (MIP-02).
 *
 * The delivery flow:
 *   WelcomeEvent (unsigned rumor, kind:444)
 *     → SealedRumorEvent (kind:13, encrypted with sender's key)
 *       → GiftWrapEvent (kind:1059, encrypted with ephemeral key to recipient)
 *
 * CRITICAL: The Commit that adds the new member MUST be confirmed by relays
 * BEFORE calling [wrapForRecipient], to prevent MLS state forks.
 */
object WelcomeGiftWrap {
    /**
     * Wraps a WelcomeEvent through the full NIP-59 gift wrap pipeline.
     *
     * @param welcomeBase64 base64-encoded MLS Welcome message
     * @param keyPackageEventId event ID of the KeyPackage consumed for this invitation
     * @param relays relays where the new member should look for GroupEvents
     * @param recipientPubKey public key of the new member being invited
     * @param signer the sender's signer (used to seal the rumor)
     * @param createdAt timestamp for the welcome event (defaults to now)
     * @return GiftWrapEvent ready to publish to relays
     */
    suspend fun wrapForRecipient(
        welcomeBase64: String,
        keyPackageEventId: HexKey,
        relays: List<NormalizedRelayUrl>,
        recipientPubKey: HexKey,
        signer: NostrSigner,
        nostrGroupId: HexKey? = null,
        createdAt: Long = TimeUtils.now(),
    ): GiftWrapEvent {
        // Step 1: Build the WelcomeEvent template directly as an unsigned rumor.
        // Per NIP-59 rumors MUST have an empty sig field, so we skip the outer
        // signature entirely and let the SealedRumorEvent carry authorship.
        val welcomeTemplate =
            WelcomeEvent.build(
                welcomeBase64 = welcomeBase64,
                keyPackageEventId = keyPackageEventId,
                relays = relays,
                nostrGroupId = nostrGroupId,
                createdAt = createdAt,
            )
        val welcomeRumor: WelcomeEvent =
            RumorAssembler.assembleRumor(
                pubKey = signer.pubKey,
                ev = welcomeTemplate,
            )

        // Step 2: Create a Rumor from the unsigned event and seal it (kind:13)
        val rumor = Rumor.create(welcomeRumor)
        val sealedRumor =
            SealedRumorEvent.create(
                rumor = rumor,
                encryptTo = recipientPubKey,
                signer = signer,
            )

        // Step 3: Gift wrap (kind:1059) with an ephemeral key to the recipient
        return GiftWrapEvent.create(
            event = sealedRumor,
            recipientPubKey = recipientPubKey,
        )
    }
}
