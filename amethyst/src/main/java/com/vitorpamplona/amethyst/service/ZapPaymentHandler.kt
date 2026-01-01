/**
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
package com.vitorpamplona.amethyst.service

import android.content.Context
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.math.round

class ZapPaymentHandler(
    val account: Account,
) {
    @Immutable
    data class Payable(
        val info: MyZapSplitSetup,
        val amountMilliSats: Long,
        val invoice: String,
    )

    data class UnverifiedZapSplitSetup(
        val lnAddress: String?,
        val weight: Double = 1.0,
        val relay: NormalizedRelayUrl? = null,
        val user: User? = null,
    )

    data class MyZapSplitSetup(
        val lnAddress: String,
        val weight: Double = 1.0,
        val relay: NormalizedRelayUrl? = null,
        val user: User? = null,
    )

    suspend fun zap(
        note: Note,
        amountMilliSats: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        showErrorIfNoLnAddress: Boolean,
        okHttpClient: (String) -> OkHttpClient,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<Payable>) -> Unit,
        zapType: LnZapEvent.ZapType,
    ) = withContext(Dispatchers.IO) {
        val noteEvent = note.event
        val zapSplitSetup = noteEvent?.zapSplitSetup()

        val unverifiedZapsToSend =
            if (!zapSplitSetup.isNullOrEmpty()) {
                zapSplitSetup.map { setup ->
                    when (setup) {
                        is ZapSplitSetupLnAddress -> {
                            UnverifiedZapSplitSetup(
                                lnAddress = setup.lnAddress,
                                weight = setup.weight,
                            )
                        }
                        is ZapSplitSetup -> {
                            val user = LocalCache.checkGetOrCreateUser(setup.pubKeyHex)
                            UnverifiedZapSplitSetup(
                                lnAddress = user?.info?.lnAddress(),
                                weight = setup.weight,
                                relay = setup.relay,
                                user = user,
                            )
                        }
                    }
                }
            } else if (noteEvent is LiveActivitiesEvent && noteEvent.hasHost()) {
                noteEvent.hosts().map {
                    val user = LocalCache.checkGetOrCreateUser(it.pubKey)
                    val lnAddress = user?.info?.lnAddress()
                    UnverifiedZapSplitSetup(lnAddress, relay = it.relayHint, user = user)
                }
            } else if (noteEvent is AppDefinitionEvent) {
                val appLud16 = noteEvent.appMetaData()?.lnAddress()
                if (appLud16 != null) {
                    listOf(UnverifiedZapSplitSetup(appLud16))
                } else {
                    val lud16 = note.author?.info?.lnAddress()
                    listOf(UnverifiedZapSplitSetup(lud16))
                }
            } else {
                listOf(UnverifiedZapSplitSetup(note.author?.info?.lnAddress()))
            }

        if (showErrorIfNoLnAddress) {
            val errors = unverifiedZapsToSend.filter { it.lnAddress.isNullOrBlank() }
            errors.forEach {
                val message =
                    if (it.user != null) {
                        stringRes(
                            context,
                            R.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats,
                            it.user.toBestDisplayName(),
                        )
                    } else {
                        stringRes(context, R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats)
                    }

                onError(
                    stringRes(context, R.string.missing_lud16),
                    message,
                    it.user,
                )
            }
        }

        val zapsToSend =
            unverifiedZapsToSend.mapNotNull {
                if (it.lnAddress != null) {
                    MyZapSplitSetup(
                        it.lnAddress,
                        it.weight,
                        it.relay,
                        it.user,
                    )
                } else {
                    null
                }
            }

        onProgress(0.02f)

        val splitZapRequests = signAllZapRequests(note, pollOption, message, zapType, zapsToSend)

        if (splitZapRequests.isEmpty()) {
            onProgress(0.00f)
            return@withContext
        } else {
            onProgress(0.05f)
        }

        val payables =
            assembleAllInvoices(
                requests = splitZapRequests,
                totalAmountMilliSats = amountMilliSats,
                message = message,
                okHttpClient = okHttpClient,
                onError = onError,
                onProgress = { onProgress(it * 0.7f + 0.05f) },
                context = context,
            )

        if (payables.isEmpty()) {
            onProgress(0.00f)
            return@withContext
        } else {
            onProgress(0.75f)
        }

        if (account.nip47SignerState.hasWalletConnectSetup()) {
            payViaNWC(payables, note, onError = onError, onProgress = {
                onProgress(it * 0.25f + 0.75f) // keeps within range.
            }, context)
            // onProgress(1f)
        } else {
            onPayViaIntent(payables.toImmutableList())
            onProgress(0f)
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
        val inputSetup: MyZapSplitSetup,
        val zapRequest: LnZapRequestEvent?,
    )

    suspend fun signAllZapRequests(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        zapsToSend: List<MyZapSplitSetup>,
    ): List<ZapRequestReady> =
        mapNotNullAsync(zapsToSend) { next: MyZapSplitSetup ->
            // makes sure the author receives the zap event
            val authorRelayList = note.author?.inboxRelays()?.toSet() ?: emptySet()

            // makes sure the zap split user receives the zap event
            val userRelayList = next.user?.inboxRelays()?.toSet() ?: emptySet()

            val noteEvent = note.event

            val zapRequest =
                if (zapType != LnZapEvent.ZapType.NONZAP && noteEvent != null) {
                    account.createZapRequestFor(noteEvent, pollOption, message, zapType, next.user, userRelayList + authorRelayList)
                } else {
                    null
                }

            ZapRequestReady(next, zapRequest)
        }

    suspend fun assembleAllInvoices(
        requests: List<ZapRequestReady>,
        totalAmountMilliSats: Long,
        message: String,
        okHttpClient: (String) -> OkHttpClient,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
    ): List<Payable> {
        var progressAllPayments = 0.00f
        val totalWeight = requests.sumOf { it.inputSetup.weight }

        return mapNotNullAsync(requests) { splitZapRequestPair: ZapRequestReady ->
            try {
                assembleInvoice(
                    lud16 = splitZapRequestPair.inputSetup.lnAddress,
                    splitSetup = splitZapRequestPair.inputSetup,
                    nostrZapRequest = splitZapRequestPair.zapRequest,
                    zapValue = calculateZapValue(totalAmountMilliSats, splitZapRequestPair.inputSetup.weight, totalWeight),
                    message = message,
                    okHttpClient = okHttpClient,
                    onProgressStep = { percentStepForThisPayment ->
                        progressAllPayments += percentStepForThisPayment / requests.size
                        onProgress(progressAllPayments)
                    },
                    context = context,
                )
            } catch (e: LightningAddressResolver.LightningAddressError) {
                onError(e.title, e.msg, splitZapRequestPair.inputSetup.user)
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError(
                    stringRes(
                        context,
                        R.string.error_unable_to_fetch_invoice,
                    ),
                    stringRes(
                        context,
                        R.string.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error,
                        e.message,
                    ),
                    null,
                )
                null
            }
        }
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
    ): List<Paid> {
        var progressAllPayments = 0.00f

        return mapNotNullAsync(
            items = payables,
            runRequestFor = { payable: Payable ->
                account.sendZapPaymentRequestFor(
                    bolt11 = payable.invoice,
                    zappedNote = note,
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
                                payable.info.user,
                            )
                        } else {
                            progressAllPayments += 0.5f / payables.size
                            onProgress(progressAllPayments)
                        }
                    },
                )

                progressAllPayments += 0.5f / payables.size
                onProgress(progressAllPayments)

                Paid(payable, true)
            },
        )
    }

    private suspend fun assembleInvoice(
        lud16: String,
        splitSetup: MyZapSplitSetup,
        nostrZapRequest: LnZapRequestEvent?,
        zapValue: Long,
        message: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgressStep: (percent: Float) -> Unit,
        context: Context,
    ): Payable {
        var progressThisPayment = 0.00f

        val invoice =
            LightningAddressResolver().lnAddressInvoice(
                lnAddress = lud16,
                milliSats = zapValue,
                message = message,
                nostrRequest = nostrZapRequest,
                okHttpClient = okHttpClient,
                onProgress = {
                    val step = it - progressThisPayment
                    progressThisPayment = it
                    onProgressStep(step)
                },
                context = context,
            )

        onProgressStep(1 - progressThisPayment)

        return Payable(
            info = splitSetup,
            amountMilliSats = zapValue,
            invoice = invoice,
        )
    }
}
