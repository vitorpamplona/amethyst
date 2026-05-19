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
package com.vitorpamplona.quartz.nip57Zaps.validate

import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.BigDecimal

/**
 * Pure validator for NIP-57 zap receipts (kind 9735), implementing the checks
 * spelled out in Appendix F of the spec:
 *
 *   1. The receipt's `pubkey` MUST equal the recipient's LNURL provider's
 *      advertised `nostrPubkey`.
 *   2. The `invoiceAmount` from the receipt's `bolt11` tag MUST equal the
 *      `amount` tag of the embedded zap request.
 *   3. The `lnurl` tag of the embedded zap request SHOULD equal the
 *      recipient's `lnurl`.
 *
 * Signature verification is the caller's responsibility — by the time we get
 * here the event should already have passed `Event.verify()`.
 *
 * Validation is intentionally lenient when the *zap request* lacks the
 * `amount` / `lnurl` tags (those tags are optional per NIP-57 §6). The MUST
 * check on the receipt's signer pubkey is non-negotiable when an
 * `expectedNostrPubkey` is provided.
 */
object LnZapReceiptValidator {
    sealed class Result {
        data object Valid : Result()

        data class Invalid(
            val reason: Reason,
            val detail: String? = null,
        ) : Result()

        enum class Reason {
            /** Receipt is missing its embedded zap request (description tag). */
            MISSING_ZAP_REQUEST,

            /** Receipt has no bolt11 tag, or the invoice is unparseable. */
            MISSING_OR_BAD_BOLT11,

            /** Receipt is signed by a key that is not the recipient's LNURL provider's `nostrPubkey`. */
            MISMATCHED_NOSTR_PUBKEY,

            /** bolt11 invoice amount does not equal the zap request's `amount` tag. */
            MISMATCHED_AMOUNT,

            /** Zap request's `lnurl` tag does not match the recipient's lnurl (SHOULD per spec). */
            MISMATCHED_LNURL,
        }
    }

    /**
     * Validate [receipt] against the expected provider pubkey and recipient lnurl.
     *
     * @param expectedNostrPubkey the LNURL provider's advertised `nostrPubkey`.
     *   Pass null to skip the pubkey check (e.g. resolver couldn't fetch).
     * @param expectedLnurl the recipient's lnurl in any of the three forms
     *   ([LnurlForm] handles canonicalization). Pass null to skip the check.
     * @param strictAmount when false, a receipt whose embedded zap request has
     *   no `amount` tag is accepted (matches NIP-57's "optional" wording).
     *   When true, a missing `amount` tag is treated as a failure.
     */
    fun validate(
        receipt: LnZapEvent,
        expectedNostrPubkey: HexKey?,
        expectedLnurl: String?,
        strictAmount: Boolean = false,
    ): Result {
        val zapRequest =
            receipt.zapRequest
                ?: return Result.Invalid(Result.Reason.MISSING_ZAP_REQUEST)

        // 1. Receipt signer must match LNURL provider's nostrPubkey.
        if (expectedNostrPubkey != null && !expectedNostrPubkey.equals(receipt.pubKey, ignoreCase = true)) {
            return Result.Invalid(
                Result.Reason.MISMATCHED_NOSTR_PUBKEY,
                "receipt signer ${receipt.pubKey} != provider $expectedNostrPubkey",
            )
        }

        // 2. bolt11 invoice amount must match zap request's "amount" tag.
        val invoice =
            receipt.lnInvoice()
                ?: return Result.Invalid(Result.Reason.MISSING_OR_BAD_BOLT11)

        val invoiceMillisats: BigDecimal =
            try {
                LnInvoiceUtil.getAmountInSats(invoice).multiply(BigDecimal(1000))
            } catch (_: Exception) {
                return Result.Invalid(Result.Reason.MISSING_OR_BAD_BOLT11, "could not parse bolt11")
            }

        val requestAmount = zapRequest.amountMillisats()
        if (requestAmount != null) {
            // Quartz's expect/actual BigDecimal exposes subtract + signum but not compareTo,
            // so use signum-after-subtract for value equality.
            if (invoiceMillisats.subtract(BigDecimal(requestAmount)).signum() != 0) {
                return Result.Invalid(
                    Result.Reason.MISMATCHED_AMOUNT,
                    "bolt11 invoice msats != request amount msats ($requestAmount)",
                )
            }
        } else if (strictAmount) {
            return Result.Invalid(Result.Reason.MISMATCHED_AMOUNT, "zap request has no amount tag")
        }

        // 3. lnurl tag SHOULD match recipient's lnurl.
        if (expectedLnurl != null) {
            val requestLnurl = zapRequest.lnurl()
            if (requestLnurl != null && !LnurlForm.matches(requestLnurl, expectedLnurl)) {
                return Result.Invalid(
                    Result.Reason.MISMATCHED_LNURL,
                    "request lnurl=$requestLnurl, expected=$expectedLnurl",
                )
            }
        }

        return Result.Valid
    }

    private fun LnZapRequestEvent.amountMillisats(): Long? =
        tags
            .firstOrNull { it.size > 1 && it[0] == "amount" }
            ?.get(1)
            ?.toLongOrNull()

    private fun LnZapRequestEvent.lnurl(): String? =
        tags
            .firstOrNull { it.size > 1 && it[0] == "lnurl" }
            ?.get(1)
            ?.takeIf { it.isNotBlank() }
}
