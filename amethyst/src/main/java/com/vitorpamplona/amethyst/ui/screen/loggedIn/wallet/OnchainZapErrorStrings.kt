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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import android.content.Context
import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendError
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendStage
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Maps the machine-readable [OnchainZapSendResult.Failure] coming out of the
 * KMP sender (which only carries English text) to localized UI strings.
 */
fun OnchainZapSendResult.Failure.userMessage(context: Context): String {
    val total = totalReceipts
    return if (error == OnchainZapSendError.RECEIPT_PUBLISH_FAILED && total != null) {
        pluralStringRes(
            context,
            R.plurals.onchain_send_error_publish_receipt_partial,
            total,
            publishedReceiptEventIds.size,
            total,
        )
    } else {
        stringRes(context, error.messageRes())
    }
}

/**
 * Untranslated diagnostic detail worth showing under the localized headline:
 * build/sign/dust failures carry a crafted, specific reason (e.g. insufficient
 * funds, signer tampering, which share is below dust). Other stages only carry
 * low-level exception text, which we keep out of the UI.
 */
fun OnchainZapSendResult.Failure.technicalDetail(): String? =
    when (error) {
        OnchainZapSendError.BUILD_FAILED,
        OnchainZapSendError.SIGN_FAILED,
        OnchainZapSendError.RECIPIENT_BELOW_DUST,
        -> cause?.message
        else -> null
    }

@StringRes
private fun OnchainZapSendError.messageRes(): Int =
    when (this) {
        OnchainZapSendError.BACKEND_NOT_CONFIGURED -> R.string.onchain_send_error_backend_not_configured
        OnchainZapSendError.LOAD_UTXOS_FAILED -> R.string.onchain_send_error_load_utxos
        OnchainZapSendError.BUILD_FAILED -> R.string.onchain_send_error_build
        OnchainZapSendError.RECIPIENT_BELOW_DUST -> R.string.onchain_send_error_recipient_dust
        OnchainZapSendError.SIGN_FAILED -> R.string.onchain_send_error_sign
        OnchainZapSendError.BROADCAST_FAILED -> R.string.onchain_send_error_broadcast
        OnchainZapSendError.RECEIPT_PUBLISH_FAILED -> R.string.onchain_send_error_publish_receipt
    }

@StringRes
fun OnchainZapSendStage.labelRes(): Int =
    when (this) {
        OnchainZapSendStage.LOADING_UTXOS -> R.string.onchain_stage_loading_utxos
        OnchainZapSendStage.BUILDING -> R.string.onchain_stage_building
        OnchainZapSendStage.SIGNING -> R.string.onchain_stage_signing
        OnchainZapSendStage.BROADCASTING -> R.string.onchain_stage_broadcasting
        OnchainZapSendStage.PUBLISHING -> R.string.onchain_stage_publishing
    }
