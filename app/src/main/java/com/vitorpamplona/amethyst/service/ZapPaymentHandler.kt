package com.vitorpamplona.amethyst.service

import android.content.Context
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.events.ZapSplitSetup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round

class ZapPaymentHandler(val account: Account) {

    @Immutable
    data class Payable(
        val info: ZapSplitSetup,
        val user: User?,
        val amountMilliSats: Long,
        val invoice: String
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
        zapType: LnZapEvent.ZapType
    ) = withContext(Dispatchers.IO) {
        val zapSplitSetup = note.event?.zapSplitSetup()

        val zapsToSend = if (!zapSplitSetup.isNullOrEmpty()) {
            zapSplitSetup
        } else {
            val lud16 = note.author?.info?.lud16?.trim() ?: note.author?.info?.lud06?.trim()

            if (lud16.isNullOrBlank()) {
                onError(
                    context.getString(R.string.missing_lud16),
                    context.getString(R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats)
                )
                return@withContext
            }

            listOf(ZapSplitSetup(lud16, null, weight = 1.0, true))
        }

        val totalWeight = zapsToSend.sumOf { it.weight }

        val invoicesToPayOnIntent = mutableListOf<Payable>()

        zapsToSend.forEachIndexed { index, value ->
            val outerProgressMin = index / zapsToSend.size.toFloat()
            val outerProgressMax = (index + 1) / zapsToSend.size.toFloat()

            val zapValue =
                round((amountMilliSats * value.weight / totalWeight) / 1000f).toLong() * 1000

            if (value.isLnAddress) {
                innerZap(
                    lud16 = value.lnAddressOrPubKeyHex,
                    note = note,
                    amount = zapValue,
                    pollOption = pollOption,
                    message = message,
                    context = context,
                    onError = onError,
                    onProgress = {
                        onProgress((it * (outerProgressMax - outerProgressMin)) + outerProgressMin)
                    },
                    zapType = zapType,
                    onPayInvoiceThroughIntent = {
                        invoicesToPayOnIntent.add(
                            Payable(
                                info = value,
                                user = null,
                                amountMilliSats = zapValue,
                                invoice = it
                            )
                        )
                    }
                )
            } else {
                val user = LocalCache.getUserIfExists(value.lnAddressOrPubKeyHex)
                val lud16 = user?.info?.lnAddress()

                if (lud16 != null) {
                    innerZap(
                        lud16 = lud16,
                        note = note,
                        amount = zapValue,
                        pollOption = pollOption,
                        message = message,
                        context = context,
                        onError = onError,
                        onProgress = {
                            onProgress((it * (outerProgressMax - outerProgressMin)) + outerProgressMin)
                        },
                        zapType = zapType,
                        overrideUser = user,
                        onPayInvoiceThroughIntent = {
                            invoicesToPayOnIntent.add(
                                Payable(
                                    info = value,
                                    user = user,
                                    amountMilliSats = zapValue,
                                    invoice = it
                                )
                            )
                        }
                    )
                } else {
                    onError(
                        context.getString(
                            R.string.missing_lud16
                        ),
                        context.getString(
                            R.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats,
                            user?.toBestDisplayName() ?: value.lnAddressOrPubKeyHex
                        )
                    )
                }
            }
        }

        if (invoicesToPayOnIntent.isNotEmpty()) {
            onPayViaIntent(invoicesToPayOnIntent.toImmutableList())
            onProgress(1f)
        } else {
            launch(Dispatchers.IO) {
                // Awaits for the event to come back to LocalCache.
                delay(5000)
                onProgress(1f)
            }
        }
    }

    private fun prepareZapRequestIfNeeded(
        note: Note,
        pollOption: Int?,
        message: String,
        zapType: LnZapEvent.ZapType,
        overrideUser: User? = null,
        onReady: (String?) -> Unit
    ) {
        if (zapType != LnZapEvent.ZapType.NONZAP) {
            account.createZapRequestFor(note, pollOption, message, zapType, overrideUser) { zapRequest ->
                onReady(zapRequest.toJson())
            }
        } else {
            onReady(null)
        }
    }

    private suspend fun innerZap(
        lud16: String,
        note: Note,
        amount: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayInvoiceThroughIntent: (String) -> Unit,
        zapType: LnZapEvent.ZapType,
        overrideUser: User? = null
    ) {
        onProgress(0.05f)

        prepareZapRequestIfNeeded(note, pollOption, message, zapType, overrideUser) { zapRequestJson ->
            onProgress(0.10f)

            LightningAddressResolver().lnAddressInvoice(
                lud16,
                amount,
                message,
                zapRequestJson,
                onSuccess = {
                    onProgress(0.7f)
                    if (account.hasWalletConnectSetup()) {
                        account.sendZapPaymentRequestFor(
                            bolt11 = it,
                            note,
                            onResponse = { response ->
                                if (response is PayInvoiceErrorResponse) {
                                    onProgress(0.0f)
                                    onError(
                                        context.getString(R.string.error_dialog_pay_invoice_error),
                                        context.getString(
                                            R.string.wallet_connect_pay_invoice_error_error,
                                            response.error?.message
                                                ?: response.error?.code?.toString()
                                                ?: "Error parsing error message"
                                        )
                                    )
                                } else {
                                    onProgress(1f)
                                }
                            }
                        )
                        onProgress(0.8f)
                    } else {
                        onPayInvoiceThroughIntent(it)
                        onProgress(0f)
                    }
                },
                onError = onError,
                onProgress = onProgress,
                context = context
            )
        }
    }
}
