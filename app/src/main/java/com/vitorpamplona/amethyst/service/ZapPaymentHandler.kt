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
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.screen.loggedIn.collectSuccessfulSigningOperations
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.events.ZapSplitSetup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.round

class ZapPaymentHandler(val account: Account) {
    @Immutable
    data class Payable(
        val info: ZapSplitSetup,
        val user: User?,
        val amountMilliSats: Long,
        val invoice: String,
    )

    suspend fun zap(
        note: Note,
        amountMilliSats: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<Payable>) -> Unit,
        zapType: LnZapEvent.ZapType,
    ) = withContext(Dispatchers.IO) {
        val noteEvent = note.event
        val zapSplitSetup = noteEvent?.zapSplitSetup()

        val zapsToSend =
            if (!zapSplitSetup.isNullOrEmpty()) {
                zapSplitSetup
            } else if (noteEvent is LiveActivitiesEvent && noteEvent.hasHost()) {
                noteEvent.hosts().map { ZapSplitSetup(it, null, weight = 1.0, false) }
            } else {
                val lud16 = note.author?.info?.lnAddress()

                if (lud16.isNullOrBlank()) {
                    onError(
                        context.getString(R.string.missing_lud16),
                        context.getString(
                            R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats,
                        ),
                    )
                    return@withContext
                }

                listOf(ZapSplitSetup(lud16, null, weight = 1.0, true))
            }

        onProgress(0.02f)
        signAllZapRequests(note, pollOption, message, zapType, zapsToSend) { splitZapRequestPairs ->
            if (splitZapRequestPairs.isEmpty()) {
                onProgress(0.00f)
                return@signAllZapRequests
            } else {
                onProgress(0.05f)
            }

            assembleAllInvoices(splitZapRequestPairs.toList(), amountMilliSats, message, onError, onProgress = {
                onProgress(it * 0.7f + 0.05f) // keeps within range.
            }, context) {
                if (it.isEmpty()) {
                    onProgress(0.00f)
                    return@assembleAllInvoices
                } else {
                    onProgress(0.75f)
                }

                if (account.hasWalletConnectSetup()) {
                    payViaNWC(it.values.map { it.second }, note, onError, onProgress = {
                        onProgress(it * 0.25f + 0.75f) // keeps within range.
                    }, context) {
                        // onProgress(1f)
                    }
                } else {
                    onPayViaIntent(
                        it.map {
                            Payable(
                                info = it.key.first,
                                user = null,
                                amountMilliSats = it.value.first,
                                invoice = it.value.second,
                            )
                        }.toImmutableList(),
                    )

                    onProgress(0f)
                }
            }
        }
    }

    private fun calculateZapValue(
        amountMilliSats: Long,
        weight: Double,
        totalWeight: Double,
    ): Long {
        val shareValue = amountMilliSats * (weight / totalWeight)
        val roundedZapValue = round(shareValue / 1000f).toLong() * 1000
        return roundedZapValue
    }

    suspend fun signAllZapRequests(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        zapsToSend: List<ZapSplitSetup>,
        onAllDone: suspend (MutableMap<ZapSplitSetup, String>) -> Unit,
    ) {
        collectSuccessfulSigningOperations<ZapSplitSetup, String>(
            operationsInput = zapsToSend,
            runRequestFor = { next: ZapSplitSetup, onReady ->
                if (next.isLnAddress) {
                    prepareZapRequestIfNeeded(note, pollOption, message, zapType) { zapRequestJson ->
                        if (zapRequestJson != null) {
                            onReady(zapRequestJson)
                        }
                    }
                } else {
                    val user = LocalCache.getUserIfExists(next.lnAddressOrPubKeyHex)
                    prepareZapRequestIfNeeded(note, pollOption, message, zapType, user) { zapRequestJson ->
                        if (zapRequestJson != null) {
                            onReady(zapRequestJson)
                        }
                    }
                }
            },
            onReady = onAllDone,
        )
    }

    suspend fun assembleAllInvoices(
        invoices: List<Pair<ZapSplitSetup, String>>,
        totalAmountMilliSats: Long,
        message: String,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
        onAllDone: suspend (MutableMap<Pair<ZapSplitSetup, String>, Pair<Long, String>>) -> Unit,
    ) {
        var progressAllPayments = 0.00f
        val totalWeight = invoices.sumOf { it.first.weight }

        collectSuccessfulSigningOperations<Pair<ZapSplitSetup, String>, Pair<Long, String>>(
            operationsInput = invoices,
            runRequestFor = { splitZapRequestPair: Pair<ZapSplitSetup, String>, onReady ->
                assembleInvoice(
                    splitSetup = splitZapRequestPair.first,
                    nostrZapRequest = splitZapRequestPair.second,
                    zapValue = calculateZapValue(totalAmountMilliSats, splitZapRequestPair.first.weight, totalWeight),
                    message = message,
                    onError = onError,
                    onProgressStep = { percentStepForThisPayment ->
                        progressAllPayments += percentStepForThisPayment / invoices.size
                        onProgress(progressAllPayments)
                    },
                    context = context,
                    onReady = onReady,
                )
            },
            onReady = onAllDone,
        )
    }

    suspend fun payViaNWC(
        invoices: List<String>,
        note: Note,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
        onAllDone: suspend (MutableMap<String, Boolean>) -> Unit,
    ) {
        var progressAllPayments = 0.00f

        collectSuccessfulSigningOperations<String, Boolean>(
            operationsInput = invoices,
            runRequestFor = { invoice: String, onReady ->
                account.sendZapPaymentRequestFor(
                    bolt11 = invoice,
                    zappedNote = note,
                    onSent = {
                        progressAllPayments += 0.5f / invoices.size
                        onProgress(progressAllPayments)
                        onReady(true)
                    },
                    onResponse = { response ->
                        if (response is PayInvoiceErrorResponse) {
                            progressAllPayments += 0.5f / invoices.size
                            onProgress(progressAllPayments)
                            onError(
                                context.getString(R.string.error_dialog_pay_invoice_error),
                                context.getString(
                                    R.string.wallet_connect_pay_invoice_error_error,
                                    response.error?.message
                                        ?: response.error?.code?.toString() ?: "Error parsing error message",
                                ),
                            )
                        } else {
                            progressAllPayments += 0.5f / invoices.size
                            onProgress(progressAllPayments)
                        }
                    },
                )
            },
            onReady = onAllDone,
        )
    }

    private fun assembleInvoice(
        splitSetup: ZapSplitSetup,
        nostrZapRequest: String,
        zapValue: Long,
        message: String,
        onError: (String, String) -> Unit,
        onProgressStep: (percent: Float) -> Unit,
        context: Context,
        onReady: (Pair<Long, String>) -> Unit,
    ) {
        var progressThisPayment = 0.00f

        var user: User? = null
        val lud16 =
            if (splitSetup.isLnAddress) {
                splitSetup.lnAddressOrPubKeyHex
            } else {
                user = LocalCache.getUserIfExists(splitSetup.lnAddressOrPubKeyHex)
                user?.info?.lnAddress()
            }

        if (lud16 != null) {
            LightningAddressResolver()
                .lnAddressInvoice(
                    lnaddress = lud16,
                    milliSats = zapValue,
                    message = message,
                    nostrRequest = nostrZapRequest,
                    onError = onError,
                    onProgress = {
                        val step = it - progressThisPayment
                        progressThisPayment = it
                        onProgressStep(step)
                    },
                    context = context,
                    onSuccess = {
                        onProgressStep(1 - progressThisPayment)
                        onReady(Pair(zapValue, it))
                    },
                )
        } else {
            onError(
                context.getString(
                    R.string.missing_lud16,
                ),
                context.getString(
                    R.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats,
                    user?.toBestDisplayName() ?: splitSetup.lnAddressOrPubKeyHex,
                ),
            )
        }
    }

    private fun prepareZapRequestIfNeeded(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        overrideUser: User? = null,
        onReady: (String?) -> Unit,
    ) {
        if (zapType != LnZapEvent.ZapType.NONZAP) {
            account.createZapRequestFor(note, pollOption, message, zapType, overrideUser) { zapRequest ->
                onReady(zapRequest.toJson())
            }
        } else {
            onReady(null)
        }
    }
}
