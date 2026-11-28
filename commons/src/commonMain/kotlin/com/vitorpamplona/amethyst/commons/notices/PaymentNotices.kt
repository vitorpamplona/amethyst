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

import com.vitorpamplona.amethyst.commons.model.Bolt12ZapFailure
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response

/** What an LNURL server said when it refused a request. */
sealed interface LnurlFailureDetail {
    /** The server's own `reason`/`message`. */
    data class ServerMessage(
        val text: String,
    ) : LnurlFailureDetail

    /** No message in the body: only the HTTP status (and its reason phrase, possibly blank). */
    data class HttpStatus(
        val code: Int,
        val reason: String,
    ) : LnurlFailureDetail
}

/** Why fetching a BOLT11 invoice from a lightning address (LUD-06/16) failed. */
sealed interface InvoiceNotice : UserNotice {
    data class CannotAssembleLnurl(
        val lnAddress: String,
    ) : InvoiceNotice

    data class ServiceUnavailable(
        val url: String,
        val lnAddress: String,
        val detail: LnurlFailureDetail,
    ) : InvoiceNotice

    data class CannotResolve(
        val url: String,
        val lnAddress: String,
        val cause: String?,
    ) : InvoiceNotice

    data class CannotFetch(
        val callback: String,
        val detail: LnurlFailureDetail,
    ) : InvoiceNotice

    data class AddressJsonInvalid(
        val lnAddress: String,
    ) : InvoiceNotice

    data class CallbackNotFound(
        val lnAddress: String,
    ) : InvoiceNotice

    data class InvoiceJsonInvalid(
        val lnAddress: String,
    ) : InvoiceNotice

    /** The receiver's wallet answered with an error [reason] instead of an invoice. */
    data class WalletRefused(
        val lnAddress: String,
        val reason: String,
    ) : InvoiceNotice

    /** The amount is outside the receiver's LUD-06 sendable range. */
    sealed interface AmountOutOfRange : InvoiceNotice

    /** The amount is below the receiver's LUD-06 `minSendable`, here converted to whole sats. */
    data class AmountBelowMinimum(
        val lnAddress: String,
        val minSats: Long,
    ) : AmountOutOfRange

    /** The amount is above the receiver's LUD-06 `maxSendable`, here converted to whole sats. */
    data class AmountAboveMaximum(
        val lnAddress: String,
        val maxSats: Long,
    ) : AmountOutOfRange

    /** The receiver's wallet answered without a `pr` element. */
    data class InvoiceMissing(
        val lnAddress: String,
    ) : InvoiceNotice

    /** The invoice is for a different amount than requested. */
    data class WrongAmount(
        val invoiceSats: Long,
        val lnAddress: String,
        val expectedSats: Long,
    ) : InvoiceNotice

    /** An unexpected failure while creating a zap invoice. */
    data class ReceiverFailed(
        val message: String?,
    ) : InvoiceNotice

    /** An unexpected failure while creating an invoice, with whatever [message] it carried. */
    data class Unexpected(
        val message: String?,
    ) : InvoiceNotice
}

/** Why a NIP-47 request did not settle. */
sealed interface NwcFailure {
    /** The reply could not be decrypted or parsed. */
    data object Unreadable : NwcFailure

    /** The wallet refused, and maybe said why. */
    data class Refused(
        val message: String?,
    ) : NwcFailure

    /** No reply arrived before the client gave up waiting. */
    data object TimedOut : NwcFailure
}

/**
 * Why a NIP-47 request did not go through, or null when the wallet settled it. See
 * `nwcFailureDetail` in `commonsUI` for why each case is told apart this way.
 */
fun Response?.nwcFailure(): NwcFailure? =
    when (this) {
        null -> NwcFailure.Unreadable
        is IErrorResponseLike -> NwcFailure.Refused(errorMessage())
        else -> null
    }

/** A CLINK debit service (kind 21002) refused to pay, or never answered ([detail] null). */
data class ClinkDebitFailed(
    val detail: String?,
) : UserNotice

/**
 * A cashu melt — paying a BOLT-11 with ecash — that did not go through, or went through but
 * could not be recorded. Only facts travel here: the melt exceptions carry one of these and
 * `UserNoticeText` words it, so no English sentence reaches the screen. Every case but
 * [PaidUnrecorded] leaves the wallet whole — nothing was spent, or the sats set aside for the
 * melt are back — so the payment may be retried through another rail.
 */
sealed interface CashuMeltNotice : UserNotice {
    /** No mint can fund the invoice with room for its fees. [heldSats] is null when nothing looked it up. */
    data class CannotFund(
        val heldSats: Long?,
        val neededSats: Long,
    ) : CashuMeltNotice

    /** The mint would not quote the invoice; [detail] is its own error text. */
    data class NoQuote(
        val detail: String,
    ) : CashuMeltNotice

    /** The mint quoted [quotedSats] for an invoice of [invoiceSats]. */
    data class WrongQuote(
        val invoiceSats: Long,
        val quotedSats: Long,
    ) : CashuMeltNotice

    /** The mint asks [feeSats] of fees for an invoice of [invoiceSats]; the wallet allows [maxFeeSats]. */
    data class FeeTooHigh(
        val invoiceSats: Long,
        val feeSats: Long,
        val maxFeeSats: Long,
    ) : CashuMeltNotice

    /** The mint refused the melt without spending anything; [detail] is its own error text. */
    data class Refused(
        val detail: String,
    ) : CashuMeltNotice

    /** The mint refused the melt after the wallet had swapped proofs for it; those [returnedSats] are back. */
    data class Refunded(
        val returnedSats: Long,
        val detail: String,
    ) : CashuMeltNotice

    /**
     * The signer denied the swap that prepares a melt, so the mint never saw the invoice.
     * [returnedSats] are back in the wallet; [recoverableFromSeedSats] come back only with
     * "Recover from seed".
     */
    data class Aborted(
        val returnedSats: Long,
        val recoverableFromSeedSats: Long = 0L,
    ) : CashuMeltNotice

    /**
     * The invoice was paid [paidSats]; only the wallet's own recording failed. Paying again
     * would pay the recipient twice. [recoverableFromSeedSats] is change that comes back only
     * with "Recover from seed".
     */
    data class PaidUnrecorded(
        val paidSats: Long,
        val recoverableFromSeedSats: Long = 0L,
    ) : CashuMeltNotice

    /** Anything else that stopped a melt; [detail] is the technical error text. */
    data class Failed(
        val detail: String,
    ) : CashuMeltNotice
}

/** How a melt that pays an invoice ended; the screen words a [CashuMeltNotice] itself. */
sealed interface CashuMeltOutcome {
    /** The invoice is settled: it took [paidSats] and the mint kept [feeSats] on top. */
    data class Paid(
        val paidSats: Long,
        val feeSats: Long,
        /** The Lightning preimage the mint returned. */
        val preimage: String?,
    ) : CashuMeltOutcome

    /**
     * The invoice is settled all the same; only the wallet's own recording failed
     * ([failure]). Never a reason to pay again: that would pay the recipient twice.
     */
    data class PaidUnrecorded(
        val failure: CashuMeltNotice.PaidUnrecorded,
    ) : CashuMeltOutcome

    data class NotPaid(
        val failure: CashuMeltNotice,
    ) : CashuMeltOutcome
}

/** No wallet app could open the invoice. */
data object NoWalletFound : UserNotice

sealed interface ZapNotice : UserNotice {
    /** A recipient has neither a lightning address nor a BOLT12 route. */
    data class MissingLnAddress(
        val userName: String?,
    ) : ZapNotice

    /** The NWC wallet did not pay a zap invoice. */
    data class PayInvoiceFailed(
        val failure: NwcFailure,
    ) : ZapNotice

    /** The BOLT12 offer was paid but produced no valid zap receipt. */
    data class Bolt12ReceiptFailed(
        val failure: Bolt12ZapFailure,
    ) : ZapNotice

    data class Bolt12PaymentFailed(
        val detail: String,
    ) : ZapNotice

    data object Bolt12PaymentTimedOut : ZapNotice

    /** The BOLT11 retry of a refused BOLT12 offer failed too. */
    data class FallbackFailed(
        val message: String,
    ) : ZapNotice
}

/** Podcasting-2.0 value-for-value payments. */
sealed interface V4VNotice : UserNotice {
    data object NoRecipients : V4VNotice

    data object KeysendNotSupported : V4VNotice

    data object KeysendRequiresNwc : V4VNotice

    data class PaymentFailed(
        val failure: NwcFailure,
    ) : V4VNotice
}
