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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeGiftWrap
import com.vitorpamplona.quartz.marmot.mls.messages.CommitResult
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Result of wrapping a Welcome for delivery.
 */
data class WelcomeDelivery(
    val giftWrapEvent: GiftWrapEvent,
    val recipientPubKey: HexKey,
)

/**
 * Handles wrapping and sending MLS Welcome messages to new group members.
 *
 * After adding a member to an MLS group (via MlsGroupManager.addMember()),
 * the [CommitResult] contains Welcome bytes that must be delivered to the
 * new member through NIP-59 gift wrapping.
 *
 * **CRITICAL timing:** The Commit event (kind:445) MUST be published to
 * relays BEFORE calling [wrapWelcome]. This prevents MLS state forks
 * where the new member joins at a different epoch than the group.
 *
 * Delivery pipeline:
 *   Welcome bytes → base64 → WelcomeEvent (kind:444, unsigned rumor)
 *     → SealedRumorEvent (kind:13, encrypted with sender's key)
 *       → GiftWrapEvent (kind:1059, encrypted with ephemeral key)
 */
class MarmotWelcomeSender(
    private val signer: NostrSigner,
) {
    /**
     * Wrap Welcome bytes from a CommitResult for delivery to a new member.
     *
     * @param commitResult the result from MlsGroupManager.addMember()
     * @param recipientPubKey public key of the new member being invited
     * @param keyPackageEventId event ID of the KeyPackage that was consumed
     * @param relays relays where the new member should subscribe for GroupEvents
     * @return the gift-wrapped event ready for publishing, or null if no Welcome in CommitResult
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun wrapWelcome(
        commitResult: CommitResult,
        recipientPubKey: HexKey,
        keyPackageEventId: HexKey,
        relays: List<NormalizedRelayUrl>,
        nostrGroupId: HexKey? = null,
    ): WelcomeDelivery? {
        val welcomeBytes = commitResult.welcomeBytes ?: return null

        val welcomeBase64 = Base64.encode(welcomeBytes)

        val giftWrap =
            WelcomeGiftWrap.wrapForRecipient(
                welcomeBase64 = welcomeBase64,
                keyPackageEventId = keyPackageEventId,
                relays = relays,
                recipientPubKey = recipientPubKey,
                signer = signer,
                nostrGroupId = nostrGroupId,
            )

        return WelcomeDelivery(
            giftWrapEvent = giftWrap,
            recipientPubKey = recipientPubKey,
        )
    }

    /**
     * Wrap Welcome bytes directly (not from a CommitResult).
     *
     * Useful when the Welcome bytes are available separately from the
     * commit flow (e.g., re-sending a Welcome after a failed delivery).
     *
     * @param welcomeBytes raw MLS Welcome message bytes
     * @param recipientPubKey public key of the new member
     * @param keyPackageEventId event ID of the consumed KeyPackage
     * @param relays relays for the new member to subscribe to
     * @return the gift-wrapped event ready for publishing
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun wrapWelcomeBytes(
        welcomeBytes: ByteArray,
        recipientPubKey: HexKey,
        keyPackageEventId: HexKey,
        relays: List<NormalizedRelayUrl>,
        nostrGroupId: HexKey? = null,
    ): WelcomeDelivery {
        val welcomeBase64 = Base64.encode(welcomeBytes)

        val giftWrap =
            WelcomeGiftWrap.wrapForRecipient(
                welcomeBase64 = welcomeBase64,
                keyPackageEventId = keyPackageEventId,
                relays = relays,
                recipientPubKey = recipientPubKey,
                signer = signer,
                nostrGroupId = nostrGroupId,
            )

        return WelcomeDelivery(
            giftWrapEvent = giftWrap,
            recipientPubKey = recipientPubKey,
        )
    }
}
