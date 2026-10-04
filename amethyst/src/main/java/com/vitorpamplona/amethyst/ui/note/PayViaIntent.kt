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
package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlin.coroutines.cancellation.CancellationException

fun payViaIntent(
    invoice: String,
    context: Context,
    noWalletFound: String,
    onPaid: () -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, "lightning:$invoice".toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        context.startActivity(intent)
        onPaid()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // don't display ugly error messages
        // if (e.message != null) {
        //   onError(stringRes(Res.string.no_wallet_found_with_error, e.message!!))
        // } else {
        onError(noWalletFound)
        // }
    }
}

/**
 * Hands a reusable BOLT12 offer (`lno1…`, from a recipient's kind:10058) off to an
 * installed wallet. Unlike a BOLT11 invoice, a BOLT12 offer is NOT a `lightning:`
 * payload — that scheme is defined for `lnbc…` invoices. Offers travel as the `lno`
 * parameter of a BIP21/BIP321 bitcoin URI (`bitcoin:?lno=lno1…`), where the on-chain
 * address is optional so a Lightning-only offer stands on its own. The wallet resolves
 * the offer, collects the amount, and completes the payment; this is a plain intent,
 * not a NIP-57/NIP-B1 zap, so it produces no Nostr receipt.
 */
fun payViaBolt12Intent(
    offer: String,
    context: Context,
    noWalletFound: String,
    onPaid: () -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, "bitcoin:?lno=$offer".toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        context.startActivity(intent)
        onPaid()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        onError(noWalletFound)
    }
}
