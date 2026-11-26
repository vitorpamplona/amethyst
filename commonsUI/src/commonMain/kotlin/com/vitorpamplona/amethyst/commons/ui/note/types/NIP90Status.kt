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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_encrypted_pill
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_feedback_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_amount
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_status_error
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_status_partial
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_status_payment_required
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_status_processing
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_status_success
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.CompactInvoiceLabel
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.MayBeInvoicePreview
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip90Dvms.status.firstAmount

/**
 * NIP-90 job feedback (kind 7000), read as the spec defines it:
 * - `["status", <status>, <extra-info>]`: one of [PAYMENT_REQUIRED], [PROCESSING], [ERROR],
 *   [SUCCESS] or [PARTIAL], with optional human-readable extra information in the third slot;
 * - `["amount", <millisats>, <bolt11>]`: the payment the service provider asks for;
 * - `e` the job request and `p` the customer;
 * - `content`: empty, a sample of the results so far, or ciphertext when the request was encrypted.
 */
@Immutable
class DvmFeedback(
    /** The status code (`status` slot 1), or null when the event carries none. */
    val status: String?,
    /** The `status` tag's human-readable extra info (slot 2), or null. */
    val extraInfo: String?,
    /** The requested payment, in millisats. */
    val amountMsats: Long?,
    /** The invoice to pay it with, when the provider sent one. */
    val bolt11: String?,
    /** True when `content` is ciphertext for the customer (an `encrypted` tag). */
    val encrypted: Boolean,
) {
    companion object {
        const val PAYMENT_REQUIRED = "payment-required"
        const val PROCESSING = "processing"
        const val ERROR = "error"
        const val SUCCESS = "success"
        const val PARTIAL = "partial"
    }
}

fun DvmStatusEvent.feedback(): DvmFeedback {
    val status = status()
    val amount = firstAmount()
    return DvmFeedback(
        status = status?.code,
        extraInfo = status?.extraInfo?.ifBlank { null },
        amountMsats = amount?.amount?.takeIf { it > 0 },
        bolt11 = amount?.lnInvoice?.trim()?.ifBlank { null },
        encrypted = tags.isDvmEncrypted(),
    )
}

/**
 * What the feedback says in words, for the DVM feed screen: the status tag's extra info, else the
 * content when it is text (DVMs often write their message there), else null.
 */
fun DvmStatusEvent.feedbackMessage(): String? = feedback().extraInfo ?: content.trim().ifBlank { null }?.takeUnless { tags.isDvmEncrypted() || looksLikeOpaquePayload(it) }

private class DvmStatusLook(
    val label: String,
    val tone: KindTone,
    val icon: MaterialSymbol,
)

@Composable
private fun dvmStatusLook(status: String?): DvmStatusLook =
    when (status) {
        DvmFeedback.PAYMENT_REQUIRED -> DvmStatusLook(stringRes(Res.string.kc_dvm_status_payment_required), KindTone.Money, MaterialSymbols.Paid)
        DvmFeedback.PROCESSING -> DvmStatusLook(stringRes(Res.string.kc_dvm_status_processing), KindTone.Primary, MaterialSymbols.HourglassTop)
        DvmFeedback.ERROR -> DvmStatusLook(stringRes(Res.string.kc_dvm_status_error), KindTone.Negative, MaterialSymbols.Error)
        DvmFeedback.SUCCESS -> DvmStatusLook(stringRes(Res.string.kc_dvm_status_success), KindTone.Positive, MaterialSymbols.CheckCircle)
        DvmFeedback.PARTIAL -> DvmStatusLook(stringRes(Res.string.kc_dvm_status_partial), KindTone.Warning, MaterialSymbols.HourglassBottom)
        // A status the spec does not define: the provider's own word, made readable.
        else -> DvmStatusLook(status?.let { readableToken(it) } ?: stringRes(Res.string.kc_dvm_feedback_title), KindTone.Neutral, MaterialSymbols.Assistant)
    }

/**
 * A NIP-90 job feedback (7000): the status as a pill, the provider's extra info, the payment it
 * asks for (the invoice, payable, when it sent one), a sample of the results so far, and the job
 * request it is about, quoted within the row's budget.
 */
@Composable
fun RenderDvmStatus(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? DvmStatusEvent ?: return
    val feedback = remember(event) { event.feedback() }
    val requestId = remember(event) { event.jobRequestId() }
    val customer = remember(event) { listOfNotNull(event.customer()) }
    val look = dvmStatusLook(feedback.status)

    KindCard(
        icon = look.icon,
        title = stringRes(Res.string.kc_dvm_feedback_title),
        tone = look.tone,
        status = look.label,
    ) {
        KindCardProse(null, note, feedback.extraInfo, backgroundColor, accountViewModel, nav)

        when {
            feedback.bolt11 != null -> {
                if (kindCardCanPreview()) {
                    MayBeInvoicePreview(feedback.bolt11, accountViewModel)
                } else {
                    CompactInvoiceLabel(feedback.bolt11)
                }
            }
            // No invoice: NIP-90 lets the customer zap this event instead.
            feedback.amountMsats != null -> KindCardField(stringRes(Res.string.kc_dvm_label_amount), kindCardMsats(feedback.amountMsats))
        }

        if (feedback.encrypted) {
            KindCardMeta(listOf(stringRes(Res.string.kc_dvm_encrypted_pill)))
        } else {
            // Empty, or a sample of the results so far (a JSON payload is screened out).
            KindCardText(note, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        }

        DvmRequestOrCustomer(requestId, customer, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}
