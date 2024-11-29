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
import com.vitorpamplona.amethyst.collectSuccessfulOperations
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.events.ZapSplitSetup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.round

class ZapPaymentHandler(
    val account: Account,
) {
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
        showErrorIfNoLnAddress: Boolean,
        forceProxy: (String) -> Boolean,
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
            } else if (noteEvent is AppDefinitionEvent) {
                val appLud16 = noteEvent.appMetaData()?.lnAddress()
                if (appLud16 != null) {
                    listOf(ZapSplitSetup(appLud16, null, weight = 1.0, true))
                } else {
                    val lud16 = note.author?.info?.lnAddress()

                    if (lud16.isNullOrBlank()) {
                        if (showErrorIfNoLnAddress) {
                            onError(
                                stringRes(context, R.string.missing_lud16),
                                stringRes(
                                    context,
                                    R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats,
                                ),
                            )
                        }
                        return@withContext
                    }

                    listOf(ZapSplitSetup(lud16, null, weight = 1.0, true))
                }
            } else {
                val lud16 = note.author?.info?.lnAddress()

                if (lud16.isNullOrBlank()) {
                    if (showErrorIfNoLnAddress) {
                        onError(
                            stringRes(context, R.string.missing_lud16),
                            stringRes(
                                context,
                                R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats,
                            ),
                        )
                    }
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

            assembleAllInvoices(splitZapRequestPairs.toList(), amountMilliSats, message, showErrorIfNoLnAddress, forceProxy, onError, onProgress = {
                onProgress(it * 0.7f + 0.05f) // keeps within range.
            }, context) {
                if (it.isEmpty()) {
                    onProgress(0.00f)
                    return@assembleAllInvoices
                } else {
                    onProgress(0.75f)
                }

                if (account.hasWalletConnectSetup()) {
                    payViaNWC(it.values.map { it.invoice }, note, onError, onProgress = {
                        onProgress(it * 0.25f + 0.75f) // keeps within range.
                    }, context) {
                        // onProgress(1f)
                    }
                } else {
                    onPayViaIntent(
                        it
                            .map {
                                Payable(
                                    info = it.key.first,
                                    user = it.key.second.user,
                                    amountMilliSats = it.value.zapValue,
                                    invoice = it.value.invoice,
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

    class SignAllZapRequestsReturn(
        val zapRequestJson: String?,
        val user: User? = null,
    )

    suspend fun signAllZapRequests(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        zapsToSend: List<ZapSplitSetup>,
        onAllDone: suspend (MutableMap<ZapSplitSetup, SignAllZapRequestsReturn>) -> Unit,
    ) {
        val authorRelayList =
            note.author
                ?.pubkeyHex
                ?.let {
                    (
                        LocalCache
                            .getAddressableNoteIfExists(
                                AdvertisedRelayListEvent.createAddressTag(it),
                            )?.event as? AdvertisedRelayListEvent?
                    )?.readRelays()
                }?.toSet()

        collectSuccessfulOperations<ZapSplitSetup, SignAllZapRequestsReturn>(
            items = zapsToSend,
            runRequestFor = { next: ZapSplitSetup, onReady ->
                if (next.isLnAddress) {
                    prepareZapRequestIfNeeded(note, pollOption, message, zapType) { zapRequestJson ->
                        if (zapRequestJson != null) {
                            onReady(SignAllZapRequestsReturn(zapRequestJson))
                        }
                    }
                } else {
                    val user = LocalCache.getUserIfExists(next.lnAddressOrPubKeyHex)
                    val userRelayList =
                        (
                            (
                                LocalCache
                                    .getAddressableNoteIfExists(
                                        AdvertisedRelayListEvent.createAddressTag(next.lnAddressOrPubKeyHex),
                                    )?.event as? AdvertisedRelayListEvent?
                            )?.readRelays()?.toSet() ?: emptySet()
                        ) + (authorRelayList ?: emptySet())

                    prepareZapRequestIfNeeded(note, pollOption, message, zapType, user, userRelayList) { zapRequestJson ->
                        onReady(SignAllZapRequestsReturn(zapRequestJson, user))
                    }
                }
            },
            onReady = onAllDone,
        )
    }

    suspend fun assembleAllInvoices(
        invoices: List<Pair<ZapSplitSetup, SignAllZapRequestsReturn>>,
        totalAmountMilliSats: Long,
        message: String,
        showErrorIfNoLnAddress: Boolean,
        forceProxy: (String) -> Boolean,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
        onAllDone: suspend (MutableMap<Pair<ZapSplitSetup, SignAllZapRequestsReturn>, AssembleInvoiceReturn>) -> Unit,
    ) {
        var progressAllPayments = 0.00f
        val totalWeight = invoices.sumOf { it.first.weight }

        collectSuccessfulOperations<Pair<ZapSplitSetup, SignAllZapRequestsReturn>, AssembleInvoiceReturn>(
            items = invoices,
            runRequestFor = { splitZapRequestPair: Pair<ZapSplitSetup, SignAllZapRequestsReturn>, onReady ->
                assembleInvoice(
                    splitSetup = splitZapRequestPair.first,
                    nostrZapRequest = splitZapRequestPair.second.zapRequestJson,
                    zapValue = calculateZapValue(totalAmountMilliSats, splitZapRequestPair.first.weight, totalWeight),
                    message = message,
                    showErrorIfNoLnAddress = showErrorIfNoLnAddress,
                    forceProxy = forceProxy,
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

        collectSuccessfulOperations<String, Boolean>(
            items = invoices,
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
                                stringRes(context, R.string.error_dialog_pay_invoice_error),
                                stringRes(
                                    context,
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

    class AssembleInvoiceReturn(
        val zapValue: Long,
        val invoice: String,
    )

    private fun assembleInvoice(
        splitSetup: ZapSplitSetup,
        nostrZapRequest: String?,
        zapValue: Long,
        message: String,
        showErrorIfNoLnAddress: Boolean = true,
        forceProxy: (String) -> Boolean,
        onError: (String, String) -> Unit,
        onProgressStep: (percent: Float) -> Unit,
        context: Context,
        onReady: (AssembleInvoiceReturn) -> Unit,
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
                    forceProxy = forceProxy,
                    onError = onError,
                    onProgress = {
                        val step = it - progressThisPayment
                        progressThisPayment = it
                        onProgressStep(step)
                    },
                    context = context,
                    onSuccess = {
                        onProgressStep(1 - progressThisPayment)
                        onReady(AssembleInvoiceReturn(zapValue, it))
                    },
                )
        } else {
            if (showErrorIfNoLnAddress) {
                onError(
                    stringRes(
                        context,
                        R.string.missing_lud16,
                    ),
                    stringRes(
                        context,
                        R.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats,
                        user?.toBestDisplayName() ?: splitSetup.lnAddressOrPubKeyHex,
                    ),
                )
            }
        }
    }

    private fun prepareZapRequestIfNeeded(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        overrideUser: User? = null,
        additionalRelays: Set<String>? = null,
        onReady: (String?) -> Unit,
    ) {
        if (zapType != LnZapEvent.ZapType.NONZAP) {
            account.createZapRequestFor(note, pollOption, message, zapType, overrideUser, additionalRelays) { zapRequest ->
                onReady(zapRequest.toJson())
            }
        } else {
            onReady(null)
        }
    }
}
