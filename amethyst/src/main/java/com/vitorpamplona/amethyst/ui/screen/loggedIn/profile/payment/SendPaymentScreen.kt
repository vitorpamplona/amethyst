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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSourceResolver
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.model.DEFAULT_ONCHAIN_ZAP_SATS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.MIN_ONCHAIN_ZAP_SATS
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ClinkOfferPayer
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.FeeTier
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.rateFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.technicalDetail
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.userMessage
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.experimental.clink.offers.OfferErrorCode
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.clink.pointers.OfferPriceType
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.SegwitAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified profile payment screen. Collects amount, optional message and zap
 * type, then pays on the spot through the selected rail (Lightning address,
 * CLINK offer, on-chain NIP-BC, or a NIP-61 cashu nutzap), surfacing the
 * invoice request + payment progress in the screen itself before closing.
 *
 * Acting on this screen IS the payment confirmation: the in-app wallet rails
 * (NWC, CLINK debit) are charged directly without an extra "are you sure"
 * dialog. Only the external-wallet fallback hands off to another app (which
 * confirms on its own).
 */
@Composable
fun SendPaymentScreen(
    userHex: String,
    initialMethodKey: String?,
    lnAddressOverride: String?,
    btcAddressOverride: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.send_payment_title), nav) },
    ) { pad ->
        LoadUser(baseUserHex = userHex, accountViewModel) { user ->
            if (user != null) {
                UserFinderFilterAssemblerSubscription(user, accountViewModel)
                SendPaymentLoaded(
                    user = user,
                    initialMethodKey = initialMethodKey,
                    lnAddressOverride = lnAddressOverride,
                    btcAddressOverride = btcAddressOverride,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SendPaymentLoaded(
    user: User,
    initialMethodKey: String?,
    lnAddressOverride: String?,
    btcAddressOverride: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userInfo by observeUserInfo(user, accountViewModel)
    val lud16 =
        lnAddressOverride
            ?: userInfo?.info?.lud16?.trim()
            ?: userInfo?.info?.lud06?.trim()

    val clinkOffer = rememberProfileClinkOffer(userInfo, accountViewModel)
    // Re-peek when the profile data refreshes: the recipient's kind:10019
    // (nutzap info) often lands moments after the screen opens, and a one-shot
    // peek would keep the Cashu rail hidden for the rest of the visit.
    val cashuFunding =
        remember(user.pubkeyHex, userInfo) {
            accountViewModel.account.cashuWalletState
                .peekNutzapFunding(user.pubkeyHex)
        }
    // Cheap read, intentionally not remembered: the chain backend is wired up
    // asynchronously at boot and a frozen `remember {}` would hide the
    // on-chain rail for the whole composition if the screen won the race.
    val onchainAvailable = LocalCache.onchainBackend != null
    // When navigated from a `bitcoin` payment target, the on-chain rail pays
    // that announced address directly (plain send, no NIP-BC receipt) instead
    // of the recipient's pubkey-derived Taproot address. Re-validated here in
    // case the route was built elsewhere with an unpayable address.
    val onchainAddressTarget =
        remember(btcAddressOverride) {
            btcAddressOverride?.takeIf { SegwitAddress.isPayableMainnetAddress(it) }
        }

    // ---- "Pay from" sources -------------------------------------------------
    // The BOLT-11 rails (Lightning, CLINK offer) can be paid from any
    // configured NWC wallet or CLINK debit, or handed off to another wallet
    // app. On-chain and cashu are intrinsically tied to their own wallets,
    // so they expose a single fixed entry that just states the source.
    val nwcWallets by accountViewModel.account.settings.nwcWallets
        .collectAsStateWithLifecycle()
    val clinkDebitWallets by accountViewModel.account.settings.clinkDebitWallets
        .collectAsStateWithLifecycle()
    val externalWalletLabel = stringRes(R.string.send_payment_source_external)
    val bolt11Sources =
        remember(nwcWallets, clinkDebitWallets, externalWalletLabel) {
            (
                PaymentSourceResolver.all(nwcWallets, clinkDebitWallets).map { PaymentFromUi(it.id, it.name) } +
                    PaymentFromUi(EXTERNAL_WALLET_SOURCE_ID, externalWalletLabel, isExternal = true)
            ).toImmutableList()
        }
    var selectedBolt11SourceId by remember {
        mutableStateOf(
            accountViewModel.account.settings
                .defaultPaymentSource()
                ?.id ?: EXTERNAL_WALLET_SOURCE_ID,
        )
    }
    // Re-resolve if the selected wallet disappears (e.g. removed in Settings
    // while this screen is open).
    LaunchedEffect(bolt11Sources) {
        if (bolt11Sources.none { it.id == selectedBolt11SourceId }) {
            selectedBolt11SourceId =
                accountViewModel.account.settings
                    .defaultPaymentSource()
                    ?.id ?: EXTERNAL_WALLET_SOURCE_ID
        }
    }

    var stage by remember { mutableStateOf<PaymentFlowStage>(PaymentFlowStage.Editing) }
    var selectedMethod by remember { mutableStateOf<ProfilePaymentMethod?>(null) }
    // Once the user taps a rail themselves, late-resolving rails (NIP-05 clink
    // lookup, kind:0 refresh) must not snap the selection back to the route's
    // preferred rail.
    var userPickedMethod by remember { mutableStateOf(false) }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var zapType by remember { mutableStateOf(accountViewModel.defaultZapType()) }
    // Range the CLINK service reported back on an INVALID_AMOUNT reply.
    var clinkRange by remember { mutableStateOf<SatRange?>(null) }
    // The pointer actually paid: swapped if the service replies "Expired or
    // Moved" with a replacement noffer. Only seeded from the resolved offer
    // while unset, so a kind:0 refresh mid-flow can't discard a redirect.
    var activeOffer by remember { mutableStateOf(clinkOffer) }
    LaunchedEffect(clinkOffer) {
        if (activeOffer == null) activeOffer = clinkOffer
    }

    var feeTier by remember { mutableStateOf(FeeTier.NORMAL) }
    var fees by remember { mutableStateOf<FeeEstimates?>(null) }

    // What the on-chain rail actually pays: the announced target address, or
    // the recipient's pubkey-derived Taproot address. Exposed via long-press
    // copy on the rail chip since it's not rendered anywhere else.
    val onchainDestination =
        remember(user.pubkeyHex, onchainAddressTarget) {
            onchainAddressTarget ?: TaprootAddress.fromPubKey(user.pubkeyHex)
        }

    val methods =
        remember(lud16, clinkOffer, cashuFunding, onchainAvailable, onchainDestination) {
            buildList {
                if (!lud16.isNullOrEmpty()) add(PaymentMethodUi(ProfilePaymentMethod.LIGHTNING, copyValue = lud16))
                if (clinkOffer != null) add(PaymentMethodUi(ProfilePaymentMethod.CLINK, copyValue = clinkOffer.encode()))
                if (onchainAvailable) add(PaymentMethodUi(ProfilePaymentMethod.ONCHAIN, copyValue = onchainDestination))
                if (cashuFunding != null) {
                    add(PaymentMethodUi(ProfilePaymentMethod.CASHU, copyValue = cashuFunding.target.mintUrl))
                }
            }.toImmutableList()
        }

    // Keep the selection valid as rails resolve asynchronously (kind:0 fetch,
    // NIP-05 clink lookup). Honors the chip the user tapped on the profile
    // when that rail is (or becomes) available.
    LaunchedEffect(methods) {
        val available = methods.map { it.method }
        val preferred = ProfilePaymentMethod.fromRouteKey(initialMethodKey)
        if (selectedMethod == null || selectedMethod !in available) {
            selectedMethod = preferred?.takeIf { it in available } ?: available.firstOrNull()
        } else if (!userPickedMethod && preferred != null && preferred in available && selectedMethod != preferred && stage is PaymentFlowStage.Editing) {
            selectedMethod = preferred
        }
    }

    // Fee estimates are only needed for the on-chain rail; bounded retry copes
    // with the backend boot race and flaky networks (mirrors OnchainZapSendDialog).
    LaunchedEffect(selectedMethod == ProfilePaymentMethod.ONCHAIN) {
        if (selectedMethod != ProfilePaymentMethod.ONCHAIN || fees != null) return@LaunchedEffect
        repeat(4) { attempt ->
            if (fees != null) return@LaunchedEffect
            val backend = LocalCache.onchainBackend
            if (backend != null) {
                val newFees = runCatching { withContext(Dispatchers.IO) { backend.feeEstimates() } }.getOrNull()
                if (newFees != null) {
                    fees = newFees
                    return@LaunchedEffect
                }
            }
            if (attempt < 3) delay(1_000L * (attempt + 1))
        }
    }

    val clinkFixedPrice =
        if (selectedMethod == ProfilePaymentMethod.CLINK && activeOffer?.priceType == OfferPriceType.FIXED) {
            activeOffer?.price
        } else {
            null
        }

    val amountSats = clinkFixedPrice ?: amountInput.toLongOrNull()

    val zapAmountChoices = remember(accountViewModel) { accountViewModel.zapAmountChoices() }
    val presetAmounts =
        remember(selectedMethod, zapAmountChoices) {
            if (selectedMethod == ProfilePaymentMethod.ONCHAIN) {
                zapAmountChoices.filter { it >= MIN_ONCHAIN_ZAP_SATS }.ifEmpty { listOf(DEFAULT_ONCHAIN_ZAP_SATS) }
            } else {
                zapAmountChoices
            }.toImmutableList()
        }

    val belowOnchainMin =
        selectedMethod == ProfilePaymentMethod.ONCHAIN &&
            amountSats != null &&
            amountSats > 0 &&
            amountSats < MIN_ONCHAIN_ZAP_SATS
    val cashuInsufficient =
        selectedMethod == ProfilePaymentMethod.CASHU &&
            amountSats != null &&
            amountSats > (cashuFunding?.bestSingleMintSats ?: 0L)
    val outsideClinkRange =
        selectedMethod == ProfilePaymentMethod.CLINK &&
            clinkFixedPrice == null &&
            amountSats != null &&
            clinkRange?.let { range ->
                val min = range.min
                val max = range.max
                (min != null && amountSats < min) || (max != null && amountSats > max)
            } == true

    val amountSupportText =
        when {
            belowOnchainMin -> stringRes(R.string.send_payment_min_onchain, MIN_ONCHAIN_ZAP_SATS.toString())
            cashuInsufficient -> stringRes(R.string.send_payment_cashu_insufficient)
            selectedMethod == ProfilePaymentMethod.CASHU ->
                stringRes(
                    R.string.send_payment_cashu_balance,
                    showAmount((cashuFunding?.bestSingleMintSats ?: 0L).toBigDecimal()),
                )
            selectedMethod == ProfilePaymentMethod.CLINK && clinkRange?.min != null && clinkRange?.max != null ->
                stringRes(R.string.clink_offer_amount_range, clinkRange?.min.toString(), clinkRange?.max.toString())
            else -> null
        }

    val canSend =
        stage is PaymentFlowStage.Editing &&
            selectedMethod != null &&
            amountSats != null &&
            amountSats > 0 &&
            !belowOnchainMin &&
            !cashuInsufficient &&
            !outsideClinkRange &&
            (selectedMethod != ProfilePaymentMethod.ONCHAIN || fees != null)

    // Labels captured up front so payment callbacks (arbitrary dispatchers)
    // never need a composable context.
    val requestingInvoiceLabel = stringRes(R.string.send_payment_requesting_invoice)
    val requestingInvoiceNostrLabel = stringRes(R.string.send_payment_requesting_invoice_nostr)
    val sendingNutzapLabel = stringRes(R.string.send_payment_sending_nutzap)
    val buildingTxLabel = stringRes(R.string.send_payment_building_tx)
    val successTitle = stringRes(R.string.send_payment_success)
    val sentToWalletLabel = stringRes(R.string.send_payment_sent_to_wallet)
    val clinkNoResponseLabel = stringRes(R.string.clink_debit_no_response)
    val invoiceErrorLabel = stringRes(R.string.error_dialog_pay_invoice_error)
    val parsingErrorLabel = stringRes(R.string.error_parsing_error_message)

    // Payment callbacks arrive on IO/relay threads. Snapshot state writes are
    // thread-safe, but every other payment flow in the app marshals UI state
    // to Main (see ReusableZapButton's progress handling) — match that.
    fun postStage(newStage: PaymentFlowStage) {
        scope.launch { stage = newStage }
    }

    /**
     * Pays a BOLT-11 through the wallet picked in the "Pay from" selector
     * without an extra confirmation dialog — this screen already collected
     * the explicit amount + Pay tap, so it IS the confirmation. The external
     * entry hands off to another wallet app (which confirms on its own).
     */
    fun payBolt11(invoice: String) {
        val settings = accountViewModel.account.settings
        val pickedSource =
            if (selectedBolt11SourceId == EXTERNAL_WALLET_SOURCE_ID) {
                null
            } else {
                PaymentSourceResolver
                    .all(settings.nwcWallets.value, settings.clinkDebitWallets.value)
                    .firstOrNull { it.id == selectedBolt11SourceId }
            }
        when (val source = pickedSource) {
            is PaymentSource.Nwc -> {
                postStage(PaymentFlowStage.InProgress(stringRes(context, R.string.send_payment_paying_via, source.name)))
                accountViewModel.sendZapPaymentRequestFor(invoice, null) { response ->
                    when (response) {
                        is PayInvoiceSuccessResponse -> postStage(PaymentFlowStage.Success(successTitle))
                        is PayInvoiceErrorResponse ->
                            postStage(
                                PaymentFlowStage.Failure(
                                    response.error?.message
                                        ?: response.error?.code?.toString()
                                        ?: parsingErrorLabel,
                                ),
                            )
                        else -> {}
                    }
                }
            }

            is PaymentSource.ClinkDebit -> {
                postStage(PaymentFlowStage.InProgress(stringRes(context, R.string.send_payment_paying_via, source.name)))
                accountViewModel.payInvoiceViaClinkDebit(source.wallet.pointer, invoice) { response ->
                    postStage(
                        if (response?.isOk() == true) {
                            PaymentFlowStage.Success(successTitle)
                        } else {
                            PaymentFlowStage.Failure(response?.failureDetail() ?: clinkNoResponseLabel)
                        },
                    )
                }
            }

            null ->
                // Hop to the Main scope: this branch can be reached from
                // sendSats' IO callback, and startActivity belongs on Main.
                scope.launch {
                    payViaIntent(
                        invoice,
                        context,
                        onPaid = { postStage(PaymentFlowStage.Success(successTitle, sentToWalletLabel)) },
                        onError = { postStage(PaymentFlowStage.Failure(it)) },
                    )
                }
        }
    }

    fun sendLightning(amount: Long) {
        val address = lud16 ?: return
        stage = PaymentFlowStage.InProgress(requestingInvoiceLabel)
        accountViewModel.sendSats(
            lnAddress = address,
            user = user,
            milliSats = amount * 1000,
            message = message,
            onNewInvoice = ::payBolt11,
            onError = { _, msg -> postStage(PaymentFlowStage.Failure(msg)) },
            onProgress = {},
            context = context,
            zapType = zapType,
        )
    }

    suspend fun runClinkOfferRequest(
        useOffer: NOffer,
        amount: Long?,
        followMoved: Boolean,
    ) {
        val response = ClinkOfferPayer.requestInvoice(accountViewModel.account, useOffer, amountSats = amount)

        val bolt11 = response?.bolt11
        val movedTo =
            if (response?.code == OfferErrorCode.EXPIRED_OR_MOVED && followMoved) {
                response.latest?.let { ClinkPointerParser.parse(it) as? NOffer }
            } else {
                null
            }

        when {
            bolt11 != null -> payBolt11(bolt11)
            // Follow a relocated offer once, paying the replacement pointer.
            movedTo != null -> {
                activeOffer = movedTo
                runClinkOfferRequest(movedTo, amount, followMoved = false)
            }
            response?.code == OfferErrorCode.INVALID_AMOUNT -> {
                clinkRange = response.range
                stage =
                    PaymentFlowStage.Failure(
                        response.error?.takeIf { it.isNotBlank() }
                            ?: stringRes(context, R.string.clink_offer_invalid_amount),
                    )
            }
            else ->
                stage =
                    PaymentFlowStage.Failure(
                        response?.error?.takeIf { it.isNotBlank() } ?: invoiceErrorLabel,
                    )
        }
    }

    fun sendClink(amount: Long) {
        val offer = activeOffer ?: return
        stage = PaymentFlowStage.InProgress(requestingInvoiceNostrLabel)
        val requestAmount = if (offer.priceType == OfferPriceType.FIXED) offer.price else amount
        scope.launch { runClinkOfferRequest(offer, requestAmount, followMoved = true) }
    }

    fun sendCashu(amount: Long) {
        stage = PaymentFlowStage.InProgress(sendingNutzapLabel, progress = 0.05f)
        accountViewModel.sendNutzapToUser(
            recipientPubKey = user.pubkeyHex,
            amountSats = amount,
            message = message,
            onError = { _, msg, _ -> postStage(PaymentFlowStage.Failure(msg)) },
            onProgress = { progress -> postStage(PaymentFlowStage.InProgress(sendingNutzapLabel, progress)) },
            onSuccess = { postStage(PaymentFlowStage.Success(successTitle)) },
        )
    }

    fun sendOnchain(amount: Long) {
        val feeRate = fees?.rateFor(feeTier) ?: return
        stage = PaymentFlowStage.InProgress(buildingTxLabel)
        scope.launch {
            // Off the Main thread: the on-chain sender signs the PSBT on the
            // calling thread (only its network hops switch to IO internally).
            val result =
                withContext(Dispatchers.IO) {
                    if (onchainAddressTarget != null) {
                        // Pays the profile's announced bitcoin address directly —
                        // a plain wallet send, no NIP-BC receipt exists for it.
                        accountViewModel.account.sendOnchainToAddress(
                            recipientAddress = onchainAddressTarget,
                            amountSats = amount,
                            feeRateSatPerVByte = feeRate,
                        )
                    } else {
                        accountViewModel.account.sendOnchainZap(
                            recipientPubKey = user.pubkeyHex,
                            amountSats = amount,
                            feeRateSatPerVByte = feeRate,
                            comment = message.trim(),
                            zappedEvent = null,
                        )
                    }
                }
            stage =
                when (result) {
                    is OnchainZapSendResult.Success ->
                        PaymentFlowStage.Success(
                            successTitle,
                            stringRes(context, R.string.send_payment_onchain_txid, result.txid.shortenMiddle()),
                        )
                    is OnchainZapSendResult.Failure ->
                        PaymentFlowStage.Failure(
                            listOfNotNull(result.userMessage(context), result.technicalDetail()).joinToString("\n"),
                        )
                }
        }
    }

    val zapTypeOptions =
        if (selectedMethod == ProfilePaymentMethod.LIGHTNING) {
            rememberLightningZapTypeOptions()
        } else {
            null
        }

    val onchainWalletLabel = stringRes(R.string.send_payment_source_onchain_wallet)
    val cashuWalletLabel = stringRes(R.string.send_payment_source_cashu_wallet)
    val fromSources =
        when (selectedMethod) {
            ProfilePaymentMethod.LIGHTNING, ProfilePaymentMethod.CLINK -> bolt11Sources
            ProfilePaymentMethod.ONCHAIN ->
                remember(onchainWalletLabel) { persistentListOf(PaymentFromUi(ONCHAIN_WALLET_SOURCE_ID, onchainWalletLabel)) }
            ProfilePaymentMethod.CASHU ->
                remember(cashuWalletLabel) { persistentListOf(PaymentFromUi(CASHU_WALLET_SOURCE_ID, cashuWalletLabel, isCashu = true)) }
            null -> null
        }
    val selectedFromId =
        when (selectedMethod) {
            ProfilePaymentMethod.LIGHTNING, ProfilePaymentMethod.CLINK -> selectedBolt11SourceId
            ProfilePaymentMethod.ONCHAIN -> ONCHAIN_WALLET_SOURCE_ID
            ProfilePaymentMethod.CASHU -> CASHU_WALLET_SOURCE_ID
            null -> null
        }

    val receiptNote =
        when {
            selectedMethod == ProfilePaymentMethod.CLINK -> stringRes(R.string.send_payment_receipt_clink)
            selectedMethod == ProfilePaymentMethod.ONCHAIN && onchainAddressTarget != null ->
                stringRes(R.string.send_payment_receipt_onchain_address, onchainAddressTarget.shortenMiddle())
            selectedMethod == ProfilePaymentMethod.ONCHAIN -> stringRes(R.string.send_payment_receipt_onchain)
            selectedMethod == ProfilePaymentMethod.CASHU -> stringRes(R.string.send_payment_receipt_cashu)
            else -> null
        }

    val messageLabel =
        when {
            selectedMethod == ProfilePaymentMethod.ONCHAIN -> stringRes(R.string.note_to_receiver)
            selectedMethod == ProfilePaymentMethod.LIGHTNING && zapType == LnZapEvent.ZapType.PRIVATE ->
                stringRes(R.string.custom_zaps_add_a_message_private)
            selectedMethod == ProfilePaymentMethod.LIGHTNING && zapType == LnZapEvent.ZapType.NONZAP ->
                stringRes(R.string.custom_zaps_add_a_message_nonzap)
            else -> stringRes(R.string.custom_zaps_add_a_message)
        }

    SendPaymentContent(
        methods = methods,
        selectedMethod = selectedMethod,
        onSelectMethod = {
            userPickedMethod = true
            selectedMethod = it
        },
        fromSources = fromSources,
        selectedFromId = selectedFromId,
        onSelectFrom = { id ->
            // Only the BOLT-11 rails have a real choice; the fixed on-chain /
            // cashu entries are disabled chips and never call back, but guard
            // anyway so a stray id can't corrupt the lightning selection.
            if (bolt11Sources.any { it.id == id }) selectedBolt11SourceId = id
        },
        presetAmounts = presetAmounts,
        amountInput = clinkFixedPrice?.toString() ?: amountInput,
        onAmountChange = { amountInput = it },
        amountLocked = clinkFixedPrice != null,
        amountSupportText = amountSupportText,
        amountIsError = belowOnchainMin || cashuInsufficient || outsideClinkRange,
        message = message,
        onMessageChange = { message = it },
        // CLINK requests and plain address sends have no field that could
        // carry a message to the recipient, so don't collect one.
        showMessageField =
            selectedMethod != ProfilePaymentMethod.CLINK &&
                !(selectedMethod == ProfilePaymentMethod.ONCHAIN && onchainAddressTarget != null),
        messageLabel = messageLabel,
        zapTypes = zapTypeOptions,
        selectedZapType = zapType,
        onZapTypeChange = { zapType = it },
        receiptNote = receiptNote,
        stage = stage,
        canSend = canSend,
        sendLabel =
            if (amountSats != null && amountSats > 0) {
                stringRes(R.string.send_payment_pay_button, showAmount(amountSats.toBigDecimal()))
            } else {
                stringRes(R.string.send_payment_pay_button_empty)
            },
        onSend = {
            val amount = amountSats ?: return@SendPaymentContent
            when (selectedMethod) {
                ProfilePaymentMethod.LIGHTNING -> sendLightning(amount)
                ProfilePaymentMethod.CLINK -> sendClink(amount)
                ProfilePaymentMethod.ONCHAIN -> sendOnchain(amount)
                ProfilePaymentMethod.CASHU -> sendCashu(amount)
                null -> {}
            }
        },
        onDone = { nav.popBack() },
        onRetry = { stage = PaymentFlowStage.Editing },
        onMessageRecipient = {
            // Mirrors the old LN-address error dialog's affordance: open a DM
            // with the recipient, prefilled with the failure detail.
            val failureDetail = (stage as? PaymentFlowStage.Failure)?.message
            nav.nav {
                routeToMessage(user, failureDetail, accountViewModel = accountViewModel)
            }
        },
        modifier = modifier,
        extraSection =
            if (selectedMethod == ProfilePaymentMethod.ONCHAIN) {
                {
                    OnchainFeeSection(
                        feeTier = feeTier,
                        onFeeTierChange = { feeTier = it },
                        fees = fees,
                        enabled = stage is PaymentFlowStage.Editing,
                    )
                }
            } else {
                null
            },
        recipientHeader = {
            RecipientHeader(user, lud16, accountViewModel, nav)
        },
    )
}

private fun String.shortenMiddle(): String = if (length > 20) take(12) + "…" + takeLast(6) else this

/** "Pay from" id for the hand-off to another wallet app (no in-app source). */
private const val EXTERNAL_WALLET_SOURCE_ID = "external-wallet-app"

/** Fixed "Pay from" ids for the rails that are bound to their own wallet. */
private const val ONCHAIN_WALLET_SOURCE_ID = "onchain-wallet"
private const val CASHU_WALLET_SOURCE_ID = "cashu-wallet"

/**
 * The NIP-57 zap-type choices for the Lightning rail. Memoized because the
 * labels only change with the locale (which recreates the Activity), while
 * this would otherwise be rebuilt on every keystroke of the amount field.
 */
@Composable
private fun rememberLightningZapTypeOptions(): ImmutableList<ZapTypeOption> {
    val publicLabel = stringRes(R.string.zap_type_public)
    val publicExplainer = stringRes(R.string.zap_type_public_explainer)
    val privateLabel = stringRes(R.string.zap_type_private)
    val privateExplainer = stringRes(R.string.zap_type_private_explainer)
    val anonymousLabel = stringRes(R.string.zap_type_anonymous)
    val anonymousExplainer = stringRes(R.string.zap_type_anonymous_explainer)
    val nonzapLabel = stringRes(R.string.zap_type_nonzap)
    val nonzapExplainer = stringRes(R.string.zap_type_nonzap_explainer)

    return remember {
        persistentListOf(
            ZapTypeOption(LnZapEvent.ZapType.PUBLIC, publicLabel, publicExplainer),
            ZapTypeOption(LnZapEvent.ZapType.PRIVATE, privateLabel, privateExplainer),
            ZapTypeOption(LnZapEvent.ZapType.ANONYMOUS, anonymousLabel, anonymousExplainer),
            ZapTypeOption(LnZapEvent.ZapType.NONZAP, nonzapLabel, nonzapExplainer),
        )
    }
}

@Composable
private fun RecipientHeader(
    user: User,
    lud16: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        UserPicture(user, 44.dp, accountViewModel = accountViewModel, nav = nav)
        Column {
            UsernameDisplay(user, accountViewModel = accountViewModel)
            if (lud16 != null) {
                Text(
                    text = lud16,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnchainFeeSection(
    feeTier: FeeTier,
    onFeeTierChange: (FeeTier) -> Unit,
    fees: FeeEstimates?,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringRes(R.string.send_payment_onchain_fee),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipFlowRow {
            FeeTier.entries.forEach { tier ->
                val rate = fees?.rateFor(tier)
                FilterChip(
                    selected = feeTier == tier,
                    enabled = enabled && fees != null,
                    onClick = { onFeeTierChange(tier) },
                    label = {
                        Text(
                            if (rate != null) {
                                "${stringRes(tier.labelRes)} · " +
                                    stringRes(R.string.onchain_send_fee_rate_eta, "%.1f".format(rate), stringRes(tier.etaLabelRes))
                            } else {
                                stringRes(tier.labelRes)
                            },
                        )
                    },
                )
            }
        }
    }
}
