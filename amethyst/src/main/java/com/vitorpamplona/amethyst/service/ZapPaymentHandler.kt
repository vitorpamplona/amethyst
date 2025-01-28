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
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource.user
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.BaseZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.AppDefinitionEvent
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
        val info: BaseZapSplitSetup,
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
        onError: (String, String, User?) -> Unit,
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
                noteEvent.hosts().map { ZapSplitSetup(it.pubKeyHex, it.relay, weight = 1.0) }
            } else if (noteEvent is AppDefinitionEvent) {
                val appLud16 = noteEvent.appMetaData()?.lnAddress()
                if (appLud16 != null) {
                    listOf(ZapSplitSetupLnAddress(appLud16, weight = 1.0))
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
                                note.author,
                            )
                        }
                        return@withContext
                    }

                    listOf(ZapSplitSetupLnAddress(lud16, weight = 1.0))
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
                            note.author,
                        )
                    }
                    return@withContext
                }

                listOf(ZapSplitSetupLnAddress(lud16, weight = 1.0))
            }

        onProgress(0.02f)
        signAllZapRequests(note, pollOption, message, zapType, zapsToSend) { splitZapRequestPairs ->
            if (splitZapRequestPairs.isEmpty()) {
                onProgress(0.00f)
                return@signAllZapRequests
            } else {
                onProgress(0.05f)
            }

            assembleAllInvoices(splitZapRequestPairs, amountMilliSats, message, showErrorIfNoLnAddress, forceProxy, onError, onProgress = {
                onProgress(it * 0.7f + 0.05f) // keeps within range.
            }, context) { payables ->
                if (payables.isEmpty()) {
                    onProgress(0.00f)
                    return@assembleAllInvoices
                } else {
                    onProgress(0.75f)
                }

                if (account.hasWalletConnectSetup()) {
                    payViaNWC(payables, note, onError = onError, onProgress = {
                        onProgress(it * 0.25f + 0.75f) // keeps within range.
                    }, context) {
                        // onProgress(1f)
                    }
                } else {
                    onPayViaIntent(
                        payables.toImmutableList(),
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

    class ZapRequestReady(
        val inputSetup: BaseZapSplitSetup,
        val zapRequestJson: String?,
        val user: User? = null,
    )

    suspend fun signAllZapRequests(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        zapsToSend: List<BaseZapSplitSetup>,
        onAllDone: suspend (List<ZapRequestReady>) -> Unit,
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

        collectSuccessfulOperations<BaseZapSplitSetup, ZapRequestReady>(
            items = zapsToSend,
            runRequestFor = { next: BaseZapSplitSetup, onReady ->
                if (next is ZapSplitSetupLnAddress) {
                    prepareZapRequestIfNeeded(note, pollOption, message, zapType) { zapRequestJson ->
                        if (zapRequestJson != null) {
                            onReady(ZapRequestReady(next, zapRequestJson))
                        }
                    }
                } else if (next is ZapSplitSetup) {
                    val user = LocalCache.getUserIfExists(next.pubKeyHex)
                    val userRelayList =
                        (
                            (
                                LocalCache
                                    .getAddressableNoteIfExists(
                                        AdvertisedRelayListEvent.createAddressTag(next.pubKeyHex),
                                    )?.event as? AdvertisedRelayListEvent?
                            )?.readRelays()?.toSet() ?: emptySet()
                        ) + (authorRelayList ?: emptySet())

                    prepareZapRequestIfNeeded(note, pollOption, message, zapType, user, userRelayList) { zapRequestJson ->
                        onReady(ZapRequestReady(next, zapRequestJson, user))
                    }
                }
            },
            onReady = onAllDone,
        )
    }

    suspend fun assembleAllInvoices(
        requests: List<ZapRequestReady>,
        totalAmountMilliSats: Long,
        message: String,
        showErrorIfNoLnAddress: Boolean,
        forceProxy: (String) -> Boolean,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
        onAllDone: suspend (List<Payable>) -> Unit,
    ) {
        var progressAllPayments = 0.00f
        val totalWeight = requests.sumOf { it.inputSetup.weight }

        collectSuccessfulOperations<ZapRequestReady, Payable>(
            items = requests,
            runRequestFor = { splitZapRequestPair: ZapRequestReady, onReady ->
                assembleInvoice(
                    splitSetup = splitZapRequestPair.inputSetup,
                    nostrZapRequest = splitZapRequestPair.zapRequestJson,
                    toUser = splitZapRequestPair.user,
                    zapValue = calculateZapValue(totalAmountMilliSats, splitZapRequestPair.inputSetup.weight, totalWeight),
                    message = message,
                    showErrorIfNoLnAddress = showErrorIfNoLnAddress,
                    forceProxy = forceProxy,
                    onError = onError,
                    onProgressStep = { percentStepForThisPayment ->
                        progressAllPayments += percentStepForThisPayment / requests.size
                        onProgress(progressAllPayments)
                    },
                    context = context,
                    onReady = onReady,
                )
            },
            onReady = onAllDone,
        )
    }

    class Paid(
        payable: Payable,
        success: Boolean,
    )

    suspend fun payViaNWC(
        payables: List<Payable>,
        note: Note,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
        onAllDone: suspend (List<Paid>) -> Unit,
    ) {
        var progressAllPayments = 0.00f

        collectSuccessfulOperations<Payable, Paid>(
            items = payables,
            runRequestFor = { payable: Payable, onReady ->
                account.sendZapPaymentRequestFor(
                    bolt11 = payable.invoice,
                    zappedNote = note,
                    onSent = {
                        progressAllPayments += 0.5f / payables.size
                        onProgress(progressAllPayments)
                        onReady(Paid(payable, true))
                    },
                    onResponse = { response ->
                        if (response is PayInvoiceErrorResponse) {
                            progressAllPayments += 0.5f / payables.size
                            onProgress(progressAllPayments)
                            onError(
                                stringRes(context, R.string.error_dialog_pay_invoice_error),
                                stringRes(
                                    context,
                                    R.string.wallet_connect_pay_invoice_error_error,
                                    response.error?.message
                                        ?: response.error?.code?.toString() ?: "Error parsing error message",
                                ),
                                payable.user,
                            )
                        } else {
                            progressAllPayments += 0.5f / payables.size
                            onProgress(progressAllPayments)
                        }
                    },
                )
            },
            onReady = onAllDone,
        )
    }

    private fun assembleInvoice(
        splitSetup: BaseZapSplitSetup,
        nostrZapRequest: String?,
        toUser: User?,
        zapValue: Long,
        message: String,
        showErrorIfNoLnAddress: Boolean = true,
        forceProxy: (String) -> Boolean,
        onError: (String, String, User?) -> Unit,
        onProgressStep: (percent: Float) -> Unit,
        context: Context,
        onReady: (Payable) -> Unit,
    ) {
        var progressThisPayment = 0.00f

        val lud16 =
            if (splitSetup is ZapSplitSetupLnAddress) {
                splitSetup.lnAddress
            } else {
                toUser?.info?.lnAddress()
            }

        if (lud16 != null) {
            LightningAddressResolver()
                .lnAddressInvoice(
                    lnaddress = lud16,
                    milliSats = zapValue,
                    message = message,
                    nostrRequest = nostrZapRequest,
                    forceProxy = forceProxy,
                    onError = { title, msg ->
                        onError(title, msg, toUser)
                    },
                    onProgress = {
                        val step = it - progressThisPayment
                        progressThisPayment = it
                        onProgressStep(step)
                    },
                    context = context,
                    onSuccess = {
                        onProgressStep(1 - progressThisPayment)
                        onReady(
                            Payable(
                                info = splitSetup,
                                user = toUser,
                                amountMilliSats = zapValue,
                                invoice = it,
                            ),
                        )
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
                        user?.toBestDisplayName() ?: splitSetup.mainId(),
                    ),
                    null,
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
