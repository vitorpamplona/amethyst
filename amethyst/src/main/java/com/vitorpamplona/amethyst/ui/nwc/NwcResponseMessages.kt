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
package com.vitorpamplona.amethyst.ui.nwc

import android.content.Context
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.shared.R
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response

// User-facing text for the ways a NIP-47 request can fail to settle. Every payment
// surface needs the same sentences, and a surface that skips one shows the user
// nothing at all — which is the failure mode these exist to prevent.

/** Shown when no kind-23195 reply arrived before the client gave up waiting. */
fun nwcTimeoutMessage(context: Context): String =
    pluralStringRes(
        context,
        R.plurals.wallet_connect_no_response_error,
        NwcSignerState.NWC_RESPONSE_TIMEOUT_SECONDS,
        NwcSignerState.NWC_RESPONSE_TIMEOUT_SECONDS,
    )

/**
 * Why a NIP-47 request did not go through, or null when the wallet settled it.
 *
 * The three cases a payment surface has to tell apart:
 * - **null receiver** — the reply could not be decrypted or parsed. `DecryptCache` swallows
 *   both failures into null, so this is the only shape an unreadable reply ever takes, and
 *   it needs a sentence: silence would leave a failed payment looking like one that worked.
 * - **[IErrorResponseLike]** — the wallet refused, and said why. Matched on the interface
 *   rather than the narrower `PayInvoiceErrorResponse` because NIP-47 does not require a
 *   wallet to echo `result_type` on an error; those refusals deserialize to the generic
 *   `NwcErrorResponse`, and checking the concrete type dropped them without a word.
 * - **anything else** — a success shape, whichever method it belongs to. Deliberately not
 *   matched against the *expected* success type: a wallet that omits `result_type` on a
 *   settled payment is guessed into `PayInvoiceSuccessResponse` by the deserializer, so
 *   demanding (say) `PayKeysendSuccessResponse` would report a real payment as unreadable.
 */
fun Response?.nwcFailureDetail(context: Context): String? =
    when (this) {
        null -> stringRes(context, R.string.wallet_connect_unreadable_response_error)
        is IErrorResponseLike -> errorMessage() ?: stringRes(context, R.string.error_parsing_error_message)
        else -> null
    }
