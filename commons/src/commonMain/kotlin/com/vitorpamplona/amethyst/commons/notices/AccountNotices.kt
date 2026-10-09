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
package com.vitorpamplona.amethyst.commons.notices

import com.vitorpamplona.amethyst.commons.actions.ConcordPinOutcome

/** Why the signer refused to sign or decrypt. */
sealed interface SignerNotice : UserNotice {
    /** The account has no private key (npub login). */
    data object ReadOnly : SignerNotice

    data object UnauthorizedDecryption : SignerNotice

    /** The external signer app is gone. */
    data object SignerNotFound : SignerNotice

    /** The signer failed in a way it doesn't classify; [cause] is shown as details. */
    class Failed(
        val cause: Throwable,
    ) : SignerNotice
}

/** A note action the user asked for that can't be done. */
sealed interface NoteActionNotice : UserNotice {
    data object CannotQuoteDraft : NoteActionNotice

    data object ReadOnlyCannotBoost : NoteActionNotice
}

/** A proof-of-work mined post that failed to sign or broadcast after the composer closed. */
class PowPublishFailed(
    val kind: Int,
    val willRetryOnRestart: Boolean,
    val message: String?,
) : UserNotice

sealed interface ConcordNotice : UserNotice {
    /** An honored Kick removed us from a community (CORD-04 §6). */
    class Kicked(
        val communityName: String?,
    ) : ConcordNotice

    /** A pin write that did not publish, for an [outcome] the user has to hear about. */
    class PinFailed(
        val outcome: ConcordPinOutcome,
    ) : ConcordNotice

    data object DissolveFailed : ConcordNotice

    data object ChannelKeyRotated : ConcordNotice

    data object ChannelKeyRotationFailed : ConcordNotice

    data object RolesUpdateFailed : ConcordNotice

    data object KickFailed : ConcordNotice
}

/** A plain BOLT12 offer payment over NWC (not a zap). */
sealed interface Bolt12OfferNotice : UserNotice {
    data object PaymentSent : Bolt12OfferNotice

    class PaymentFailed(
        val detail: String,
    ) : Bolt12OfferNotice
}

sealed interface NutzapNotice : UserNotice {
    /** A public kind:9321 on a private rumor would leak its id. */
    data object PrivateNote : NutzapNotice

    data object NoRecipient : NutzapNotice

    data object NoEvent : NutzapNotice

    class MintFailed(
        val detail: String,
    ) : NutzapNotice
}

/** Redeeming a Cashu token to the account's own lightning address. */
sealed interface CashuRedeemNotice : UserNotice {
    class Redeemed(
        val amountSats: Long,
        val feeSats: Int,
    ) : CashuRedeemNotice

    class Failed(
        val detail: String?,
    ) : CashuRedeemNotice

    /** The mint URL was refused before any request went out. */
    class UnsafeMintUrl(
        val detail: String?,
    ) : CashuRedeemNotice

    class NoLightningAddress(
        val userName: String,
    ) : CashuRedeemNotice
}
