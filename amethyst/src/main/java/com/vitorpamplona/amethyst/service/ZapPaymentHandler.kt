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
package com.vitorpamplona.amethyst.service

import android.content.Context
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.nwc.nwcFailureDetail
import com.vitorpamplona.amethyst.ui.nwc.nwcTimeoutMessage
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransactionMetadata
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.round

class ZapPaymentHandler(
    val account: Account,
) {
    @Immutable
    data class Payable(
        val info: MyZapSplitSetup,
        val amountMilliSats: Long,
        val invoice: String,
        // The signed kind 9734 this invoice was fetched with, and the message on it.
        // Carried so the NWC payment can name the payee (NWC-06 `metadata`); null for
        // a NONZAP split, which has no zap request to send.
        val zapRequest: LnZapRequestEvent? = null,
        val message: String = "",
    )

    data class UnverifiedZapSplitSetup(
        val lnAddress: String?,
        val weight: Double = 1.0,
        val relay: NormalizedRelayUrl? = null,
        val user: User? = null,
        val bolt12Offer: String? = null,
    )

    data class MyZapSplitSetup(
        val lnAddress: String,
        val weight: Double = 1.0,
        val relay: NormalizedRelayUrl? = null,
        val user: User? = null,
    )

    /** A recipient routed over BOLT12 (NIP-B1): they publish a kind:10058 [offer] and we hold an NWC wallet. */
    data class Bolt12Recipient(
        val user: User,
        val offer: String,
        val weight: Double = 1.0,
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
            when {
                !zapSplitSetup.isNullOrEmpty() -> {
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
                                    lnAddress = user?.lnAddress(),
                                    weight = setup.weight,
                                    relay = setup.relay,
                                    user = user,
                                    bolt12Offer = user?.bolt12Offers()?.firstOrNull(),
                                )
                            }
                        }
                    }
                }

                noteEvent is LiveActivitiesEvent && noteEvent.hasHost() -> {
                    noteEvent.hosts().map {
                        val user = LocalCache.checkGetOrCreateUser(it.pubKey)
                        val lnAddress = user?.lnAddress()
                        UnverifiedZapSplitSetup(lnAddress, relay = it.relayHint, user = user, bolt12Offer = user?.bolt12Offers()?.firstOrNull())
                    }
                }

                noteEvent is AppDefinitionEvent -> {
                    val appLud16 = noteEvent.appMetaData()?.lnAddress()
                    if (appLud16 != null) {
                        listOf(UnverifiedZapSplitSetup(appLud16))
                    } else {
                        val author = note.author
                        listOf(UnverifiedZapSplitSetup(author?.lnAddress(), user = author, bolt12Offer = author?.bolt12Offers()?.firstOrNull()))
                    }
                }

                else -> {
                    val author = note.author
                    listOf(
                        UnverifiedZapSplitSetup(
                            lnAddress = author?.lnAddress(),
                            user = author,
                            bolt12Offer = author?.bolt12Offers()?.firstOrNull(),
                        ),
                    )
                }
            }

        // BOLT12-first: a recipient who publishes a kind:10058 offer is zapped over
        // BOLT12 when our default NWC wallet advertises the nwc#2 `pay` method (needed for
        // the payer proof). Otherwise — no wallet, or a wallet without `pay` — the recipient
        // stays on lightning, so an unsupported wallet degrades gracefully instead of erroring.
        val canBolt12 =
            account.settings.nwcWallets.value
                .isNotEmpty() &&
                account.zaps.defaultWalletSupportsBolt12Pay()

        val bolt12Recipients =
            unverifiedZapsToSend.mapNotNull {
                val user = it.user
                if (canBolt12 && it.bolt12Offer != null && user != null) {
                    Bolt12Recipient(user, it.bolt12Offer, it.weight)
                } else {
                    null
                }
            }
        val bolt12Users = bolt12Recipients.mapTo(HashSet()) { it.user }

        val zapsToSend =
            unverifiedZapsToSend.mapNotNull {
                // Never lightning-zap a recipient already routed over BOLT12.
                if (it.user != null && it.user in bolt12Users) {
                    null
                } else if (it.lnAddress != null) {
                    MyZapSplitSetup(it.lnAddress, it.weight, it.relay, it.user)
                } else {
                    null
                }
            }

        if (showErrorIfNoLnAddress) {
            // Only a recipient with neither a BOLT12 route nor an lnAddress is unpayable.
            val errors =
                unverifiedZapsToSend.filter {
                    val routedBolt12 = canBolt12 && it.bolt12Offer != null && it.user != null
                    !routedBolt12 && it.lnAddress.isNullOrBlank()
                }
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

        // Weight is shared across both lanes so splits stay proportional regardless of rail.
        val totalWeight = bolt12Recipients.sumOf { it.weight } + zapsToSend.sumOf { it.weight }
        if (totalWeight <= 0.0) {
            onProgress(0.00f)
            return@withContext
        }

        onProgress(0.02f)

        // --- Lightning lane -----------------------------------------------------------
        if (zapsToSend.isNotEmpty()) {
            val splitZapRequests = signAllZapRequests(note, pollOption, message, zapType, zapsToSend, amountMilliSats, totalWeight)

            if (splitZapRequests.isNotEmpty()) {
                onProgress(0.05f)

                val payables =
                    assembleAllInvoices(
                        requests = splitZapRequests,
                        totalAmountMilliSats = amountMilliSats,
                        message = message,
                        okHttpClient = okHttpClient,
                        onError = onError,
                        onProgress = { onProgress(it * 0.7f + 0.05f) },
                        context = context,
                        totalWeight = totalWeight,
                    )

                if (payables.isNotEmpty()) {
                    onProgress(0.75f)

                    // Route through the user's selected default payment source. A CLINK debit takes
                    // precedence over NWC when it is the chosen default; NWC-only users are unaffected
                    // (defaultPaymentSource() resolves to their NWC wallet). No source -> wallet app.
                    when (val source = account.settings.defaultPaymentSource()) {
                        is PaymentSource.ClinkDebit -> {
                            payViaClinkDebit(payables, source.wallet.pointer, onError = onError, onProgress = {
                                onProgress(it * 0.25f + 0.75f)
                            }, context)
                        }

                        is PaymentSource.Nwc -> {
                            payViaNWC(payables, note, onError = onError, onProgress = {
                                onProgress(it * 0.25f + 0.75f) // keeps within range.
                            }, context)
                        }

                        null -> {
                            onPayViaIntent(payables.toImmutableList())
                        }
                    }
                }
            }
        }

        // --- BOLT12 lane --------------------------------------------------------------
        if (bolt12Recipients.isNotEmpty()) {
            payViaBolt12(
                recipients = bolt12Recipients,
                note = note,
                totalAmountMilliSats = amountMilliSats,
                totalWeight = totalWeight,
                message = message,
                zapType = zapType,
                onError = onError,
                onProgress = { onProgress(it * 0.25f + 0.75f) },
                context = context,
            )
        }

        onProgress(1f)
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
        totalAmountMilliSats: Long,
        // Shared across the lightning + BOLT12 lanes so a mixed split stays proportional.
        totalWeight: Double = zapsToSend.sumOf { it.weight },
    ): List<ZapRequestReady> =
        mapNotNullAsync(zapsToSend) { next: MyZapSplitSetup ->
            // makes sure the author receives the zap event
            val authorRelayList = note.author?.inboxRelays()?.toSet() ?: emptySet()

            // makes sure the zap split user receives the zap event
            val userRelayList = next.user?.inboxRelays()?.toSet() ?: emptySet()

            val noteEvent = note.event

            // Per NIP-57 §6: the zap request SHOULD carry `amount` (in msats) and
            // `lnurl` so the recipient's LNURL provider — and clients reading the
            // resulting receipt — can validate them under Appendix F.
            val splitAmount = calculateZapValue(totalAmountMilliSats, next.weight, totalWeight)
            val splitLnurl = LnurlForm.toUrl(next.lnAddress)?.let(LnurlForm::urlToBech32)

            val zapRequest =
                if (zapType != LnZapEvent.ZapType.NONZAP && noteEvent != null) {
                    account.zaps.createZapRequestFor(
                        event = noteEvent,
                        pollOption = pollOption,
                        message = message,
                        zapType = zapType,
                        toUser = next.user,
                        additionalRelays = userRelayList + authorRelayList,
                        amountMillisats = splitAmount,
                        lnurl = splitLnurl,
                    )
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
        // Shared across the lightning + BOLT12 lanes so a mixed split stays proportional.
        totalWeight: Double = requests.sumOf { it.inputSetup.weight },
    ): List<Payable> {
        var progressAllPayments = 0.00f

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
        val progress = PaymentProgress(payables.size, onProgress)

        return mapNotNullAsync(
            items = payables,
            runRequestFor = { payable: Payable ->
                account.zaps.sendZapPaymentRequestFor(
                    bolt11 = payable.invoice,
                    zappedNote = note,
                    // Dropped unless the wallet advertises NWC-06 — see NwcSignerState.
                    metadata =
                        NwcTransactionMetadata.build(
                            zapRequest = payable.zapRequest,
                            recipientIdentifier = payable.info.lnAddress,
                            comment = payable.message,
                        ),
                    onResponse = { response ->
                        progress.step()
                        response.nwcFailureDetail(context)?.let { detail ->
                            onError(
                                stringRes(context, R.string.error_dialog_pay_invoice_error),
                                stringRes(context, R.string.wallet_connect_pay_invoice_error_error, detail),
                                payable.info.user,
                            )
                        }
                    },
                    onTimeout = {
                        onError(
                            stringRes(context, R.string.error_dialog_pay_invoice_error),
                            nwcTimeoutMessage(context),
                            payable.info.user,
                        )
                    },
                )

                progress.step()

                Paid(payable, true)
            },
        )
    }

    /**
     * BOLT12 zap rail (NIP-B1). For each recipient that publishes a kind:10058 offer,
     * signs a 9737 intent, pays the offer over NWC with the intent-bound `payer_note`,
     * and (if the returned proof validates) publishes a 9736 zap — see
     * [Account.sendBolt12Zap]. Fire-and-forget like [payViaNWC]: dispatch is optimistic
     * and settlement/errors surface later through the async NWC response.
     */
    suspend fun payViaBolt12(
        recipients: List<Bolt12Recipient>,
        note: Note,
        totalAmountMilliSats: Long,
        totalWeight: Double,
        message: String,
        zapType: LnZapEvent.ZapType,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
    ) {
        val progress = PaymentProgress(recipients.size, onProgress)

        mapNotNullAsync(recipients) { recipient: Bolt12Recipient ->
            account.zaps.sendBolt12Zap(
                zappedEvent = note.event,
                recipientPubKey = recipient.user.pubkeyHex,
                offer = recipient.offer,
                amountMillisats = calculateZapValue(totalAmountMilliSats, recipient.weight, totalWeight),
                message = message,
                zapType = zapType,
                onError = { msgRes, detail ->
                    val msg = if (detail != null) stringRes(context, msgRes, detail) else stringRes(context, msgRes)
                    onError(stringRes(context, R.string.bolt12_zap_error), msg, recipient.user)
                },
                onProcessed = { progress.step() },
            )

            progress.step()

            recipient
        }
    }

    /**
     * Thread-safe progress accumulator for the parallel pay rails. Each payable advances in two
     * half-steps (request dispatched, then response/settlement), reported as a 0..1 fraction.
     * The counter is atomic because `mapNotNullAsync` runs the payables concurrently and the
     * response half-step fires from an async callback, so plain `+=` would lose updates.
     */
    private class PaymentProgress(
        payableCount: Int,
        private val onProgress: (percent: Float) -> Unit,
    ) {
        private val totalSteps = (payableCount * 2).coerceAtLeast(1)
        private val done = AtomicInteger(0)

        fun step() = onProgress(done.incrementAndGet().toFloat() / totalSteps)
    }

    /**
     * Pays each zap invoice by asking the user's CLINK debit service (kind 21002) to
     * settle the BOLT-11. The service authorizes against the account identity.
     *
     * Fire-and-forget, like the NWC rail ([payViaNWC]): the request is dispatched on the
     * account scope and each payable is reported paid optimistically so the zap UI completes
     * promptly. A `GFY`/failure (or no reply within the debit timeout) surfaces later through
     * [onError] rather than blocking the zap on the service's response.
     */
    suspend fun payViaClinkDebit(
        payables: List<Payable>,
        pointer: NDebit,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
    ): List<Paid> {
        val progress = PaymentProgress(payables.size, onProgress)

        return mapNotNullAsync(
            items = payables,
            runRequestFor = { payable: Payable ->
                account.scope.launch {
                    val response = ClinkDebitPayer.payInvoice(account, pointer, payable.invoice)
                    progress.step()
                    if (response?.isOk() != true) {
                        onError(
                            stringRes(context, R.string.error_dialog_pay_invoice_error),
                            response?.failureDetail()
                                ?: stringRes(context, R.string.clink_debit_no_response),
                            payable.info.user,
                        )
                    }
                }

                progress.step()

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

        // Only the request the provider actually accepted may be claimed as bound to
        // this invoice; see lnAddressInvoice's onZapRequestSent.
        var sentZapRequest: LnZapRequestEvent? = null

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
                onZapRequestSent = { sentZapRequest = it },
            )

        onProgressStep(1 - progressThisPayment)

        return Payable(
            info = splitSetup,
            amountMilliSats = zapValue,
            invoice = invoice,
            zapRequest = sentZapRequest,
            message = message,
        )
    }
}
