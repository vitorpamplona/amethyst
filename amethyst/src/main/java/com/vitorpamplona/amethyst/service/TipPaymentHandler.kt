/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object TipPaymentHandler {
    @Immutable
    data class Payable(
        val user: User?,
        val amount: Double,
        val address: String,
    )

    fun tip(
        note: Note,
        amount: Double,
        context: Context,
        onError: (String, String, User?) -> Unit,
    ) {
        note.author?.info?.moneroAddress()?.let { address ->
            if (!MoneroValidator.isValidAddress(address)) {
                onError(context.getString(R.string.monero), context.getString(R.string.invalid_monero_address), note.author)
                return
            }
            try {
                val sendIntent = Intent(Intent.ACTION_VIEW, "monero:$address?amount=$amount".toUri())
                context.startActivity(sendIntent)
            } catch (_: Exception) {
                onError(context.getString(R.string.monero_wallet), context.getString(R.string.no_monero_wallet_found), null)
            }
        }
    }

    fun tip(
        address: String,
        amount: Double,
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String, User?) -> Unit,
    ) {
        if (!MoneroValidator.isValidAddress(address)) {
            onError(context.getString(R.string.monero), context.getString(R.string.invalid_monero_address), null)
            return
        }
        try {
            val sendIntent = Intent(Intent.ACTION_VIEW, "monero:$address?amount=$amount".toUri())
            context.startActivity(sendIntent)
            onSuccess()
        } catch (_: Exception) {
            onError(context.getString(R.string.monero_wallet), context.getString(R.string.no_monero_wallet_found), null)
        }
    }
}
