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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.note.DVMCard
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.ObserveZapIcon
import com.vitorpamplona.amethyst.ui.note.PayViaIntentDialog
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoicePopup
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.note.elements.customZapClick
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.AppMetadata
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.events.NIP90StatusEvent
import com.vitorpamplona.quartz.events.PayInvoiceErrorResponse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DvmContentDiscoveryScreen(
    appDefinitionEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (appDefinitionEventId == null) return

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            DvmTopBar(appDefinitionEventId, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it)) {
            LoadNote(baseNoteHex = appDefinitionEventId, accountViewModel = accountViewModel) {
                it?.let { baseNote ->
                    WatchNoteEvent(
                        baseNote,
                        onNoteEventFound = {
                            DvmContentDiscoveryScreen(baseNote, accountViewModel, nav)
                        },
                        onBlank = {
                            FeedEmptyWithStatus(baseNote, stringRes(R.string.dvm_looking_for_app), accountViewModel, nav)
                        },
                        accountViewModel,
                    )
                }
            }
        }
    }
}

@Composable
fun DvmContentDiscoveryScreen(
    appDefinition: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteAuthor = appDefinition.author ?: return

    var requestEventID by
        remember(appDefinition) {
            mutableStateOf<Note?>(null)
        }

    val onRefresh = {
        accountViewModel.requestDVMContentDiscovery(noteAuthor.pubkeyHex) {
            requestEventID = it
        }
    }

    LaunchedEffect(key1 = appDefinition) {
        val existingResult = accountViewModel.cachedDVMContentDiscovery(noteAuthor.pubkeyHex)
        if (existingResult == null) {
            onRefresh()
        } else {
            requestEventID = existingResult
        }
    }

    RefresheableBox(
        onRefresh = onRefresh,
    ) {
        val myRequestEventID = requestEventID
        if (myRequestEventID != null) {
            ObserverContentDiscoveryResponse(
                appDefinition,
                myRequestEventID,
                onRefresh,
                accountViewModel,
                nav,
            )
        } else {
            // TODO: Make a good splash screen with loading animation for this DVM.
            // FeedDVM(appDefinition, null, accountViewModel, nav)
            FeedEmptyWithStatus(appDefinition, stringRes(R.string.dvm_requesting_job), accountViewModel, nav)
        }
    }
}

@Composable
fun ObserverContentDiscoveryResponse(
    appDefinition: Note,
    dvmRequestId: Note,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteAuthor = appDefinition.author ?: return
    val updateFiltersFromRelays = dvmRequestId.live().metadata.observeAsState()

    val resultFlow =
        remember(dvmRequestId) {
            accountViewModel.observeByETag<NIP90ContentDiscoveryResponseEvent>(NIP90ContentDiscoveryResponseEvent.KIND, dvmRequestId.idHex)
        }

    val latestResponse by resultFlow.collectAsStateWithLifecycle()

    if (latestResponse != null) {
        PrepareViewContentDiscoveryModels(
            noteAuthor,
            dvmRequestId.idHex,
            onRefresh,
            accountViewModel,
            nav,
        )
    } else {
        ObserverDvmStatusResponse(
            appDefinition,
            dvmRequestId.idHex,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun ObserverDvmStatusResponse(
    appDefinition: Note,
    dvmRequestId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val statusFlow =
        remember(dvmRequestId) {
            accountViewModel.observeByETag<NIP90StatusEvent>(NIP90StatusEvent.KIND, dvmRequestId)
        }

    val latestStatus by statusFlow.collectAsStateWithLifecycle()
    // TODO: Make a good splash screen with loading animation for this DVM.
    if (latestStatus != null) {
        // TODO: Make a good splash screen with loading animation for this DVM.
        latestStatus?.let {
            FeedDVM(appDefinition, it, accountViewModel, nav)
        }
    } else {
        // TODO: Make a good splash screen with loading animation for this DVM.
        FeedEmptyWithStatus(appDefinition, stringRes(R.string.dvm_waiting_status), accountViewModel, nav)
    }
}

@Composable
fun PrepareViewContentDiscoveryModels(
    dvm: User,
    dvmRequestId: String,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel =
        viewModel(
            key = "NostrNIP90ContentDiscoveryFeedViewModel${dvm.pubkeyHex}$dvmRequestId",
            factory = NostrNIP90ContentDiscoveryFeedViewModel.Factory(accountViewModel.account, dvmkey = dvm.pubkeyHex, requestid = dvmRequestId),
        )

    LaunchedEffect(key1 = dvmRequestId) {
        resultFeedViewModel.invalidateData()
    }

    RenderNostrNIP90ContentDiscoveryScreen(resultFeedViewModel, onRefresh, accountViewModel, nav)
}

@Composable
fun RenderNostrNIP90ContentDiscoveryScreen(
    resultFeedViewModel: NostrNIP90ContentDiscoveryFeedViewModel,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.fillMaxHeight()) {
        SaveableFeedState(resultFeedViewModel, null) { listState ->
            // TODO (Optional) Instead of a like reaction, do a Kind 31989 NIP89 App recommendation
            RenderFeedState(
                resultFeedViewModel,
                accountViewModel,
                listState,
                nav,
                null,
                onEmpty = {
                    FeedEmpty {
                        onRefresh()
                    }
                },
            )
        }
    }
}

@Composable
fun FeedDVM(
    appDefinitionNote: Note,
    latestStatus: NIP90StatusEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val status = latestStatus.status() ?: return

    var currentStatus by remember {
        mutableStateOf(status.description)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val card = observeAppDefinition(appDefinitionNote)

        card.cover?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(Size75dp)
                        .clip(QuoteBorder),
            )
        } ?: run { NoteAuthorPicture(appDefinitionNote, nav, accountViewModel, Size75dp) }

        Spacer(modifier = DoubleVertSpacer)

        Text(
            text = card.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = DoubleVertSpacer)
        Text(currentStatus, textAlign = TextAlign.Center)

        if (status.code == "payment-required") {
            val amountTag = latestStatus.firstAmount()
            val amount = amountTag?.amount

            val invoice = amountTag?.lnInvoice

            val thankYou = stringRes(id = R.string.dvm_waiting_to_confirm_payment)
            val nwcPaymentRequest = stringRes(id = R.string.nwc_payment_request)

            if (invoice != null) {
                val context = LocalContext.current
                Button(onClick = {
                    if (accountViewModel.account.hasWalletConnectSetup()) {
                        accountViewModel.sendZapPaymentRequestFor(
                            bolt11 = invoice,
                            zappedNote = null,
                            onSent = {
                                currentStatus = nwcPaymentRequest
                            },
                            onResponse = { response ->
                                currentStatus =
                                    if (response is PayInvoiceErrorResponse) {
                                        stringRes(
                                            context,
                                            R.string.wallet_connect_pay_invoice_error_error,
                                            response.error?.message
                                                ?: response.error?.code?.toString() ?: "Error parsing error message",
                                        )
                                    } else {
                                        thankYou
                                    }
                            },
                        )
                    } else {
                        payViaIntent(
                            invoice,
                            context,
                            onPaid = {
                                currentStatus = thankYou
                            },
                            onError = {
                                currentStatus = it
                            },
                        )
                    }
                }) {
                    val amountInInvoice =
                        try {
                            LnInvoiceUtil.getAmountInSats(invoice).toLong()
                        } catch (e: Exception) {
                            null
                        }

                    if (amountInInvoice != null) {
                        Text(text = "Pay $amountInInvoice sats to the DVM")
                    } else {
                        Text(text = "Pay Invoice from the DVM")
                    }
                }
            } else if (amount != null) {
                LoadNote(baseNoteHex = latestStatus.id, accountViewModel = accountViewModel) { stateNote ->
                    stateNote?.let {
                        ZapDVMButton(
                            baseNote = it,
                            amount = amount,
                            grayTint = MaterialTheme.colorScheme.onPrimary,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        } else if (status.code == "processing") {
            currentStatus = status.description
        } else if (status.code == "error") {
            currentStatus = status.description
        }
    }
}

@Composable
fun ZapDVMButton(
    baseNote: Note,
    amount: Long,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = Size35dp,
    iconSizeModifier: Modifier = Size20Modifier,
    animationSize: Dp = 14.dp,
    nav: INav,
) {
    val noteAuthor = baseNote.author ?: return

    var wantsToZap by remember { mutableStateOf<List<Long>?>(null) }
    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }
    var wantsToPay by
        remember(baseNote) {
            mutableStateOf<ImmutableList<ZapPaymentHandler.Payable>>(
                persistentListOf(),
            )
        }

    // Makes sure the user is loaded to get his ln address
    val userState = noteAuthor.live().metadata.observeAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableFloatStateOf(0f) }
    var hasZapped by remember { mutableStateOf(false) }

    Button(
        onClick = {
            customZapClick(
                baseNote,
                accountViewModel,
                context,
                onZappingProgress = { progress: Float ->
                    scope.launch { zappingProgress = progress }
                },
                onMultipleChoices = { options -> wantsToZap = options },
                onError = { _, message ->
                    scope.launch {
                        zappingProgress = 0f
                        showErrorMessageDialog = message
                    }
                },
                onPayViaIntent = { wantsToPay = it },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (wantsToZap != null) {
            ZapAmountChoicePopup(
                baseNote = baseNote,
                zapAmountChoices = persistentListOf(amount / 1000),
                popupYOffset = iconSize,
                accountViewModel = accountViewModel,
                onDismiss = {
                    wantsToZap = null
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = null
                },
                onError = { _, message ->
                    scope.launch {
                        zappingProgress = 0f
                        showErrorMessageDialog = message
                    }
                },
                onProgress = {
                    scope.launch(Dispatchers.Main) { zappingProgress = it }
                },
                onPayViaIntent = { wantsToPay = it },
            )
        }

        if (showErrorMessageDialog != null) {
            ErrorMessageDialog(
                title = stringRes(id = R.string.error_dialog_zap_error),
                textContent = showErrorMessageDialog ?: "",
                onClickStartMessage = {
                    baseNote.author?.let {
                        scope.launch(Dispatchers.IO) {
                            val route = routeToMessage(it, showErrorMessageDialog, accountViewModel)
                            nav.nav(route)
                        }
                    }
                },
                onDismiss = { showErrorMessageDialog = null },
            )
        }

        if (wantsToPay.isNotEmpty()) {
            PayViaIntentDialog(
                payingInvoices = wantsToPay,
                accountViewModel = accountViewModel,
                onClose = { wantsToPay = persistentListOf() },
                onError = {
                    wantsToPay = persistentListOf()
                    scope.launch {
                        zappingProgress = 0f
                        showErrorMessageDialog = it
                    }
                },
                justShowError = {
                    scope.launch {
                        showErrorMessageDialog = it
                    }
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = iconSizeModifier,
        ) {
            if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
                Spacer(ModifierWidth3dp)

                CircularProgressIndicator(
                    progress =
                        animateFloatAsState(
                            targetValue = zappingProgress,
                            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                            label = "ZapIconIndicator",
                        ).value,
                    modifier = remember { Modifier.size(animationSize) },
                    strokeWidth = 2.dp,
                    color = grayTint,
                )
            } else {
                ObserveZapIcon(
                    baseNote,
                    accountViewModel,
                ) { wasZappedByLoggedInUser ->
                    LaunchedEffect(wasZappedByLoggedInUser.value) {
                        hasZapped = wasZappedByLoggedInUser.value
                        if (wasZappedByLoggedInUser.value && !accountViewModel.account.hasDonatedInThisVersion()) {
                            delay(1000)
                            accountViewModel.markDonatedInThisVersion()
                        }
                    }

                    CrossfadeIfEnabled(targetState = wasZappedByLoggedInUser.value, label = "ZapIcon", accountViewModel = accountViewModel) {
                        if (it) {
                            ZappedIcon(iconSizeModifier)
                        } else {
                            ZapIcon(iconSizeModifier, grayTint)
                        }
                    }
                }
            }
        }

        if (hasZapped) {
            Text(text = stringRes(id = R.string.thank_you))
        } else {
            Text(text = "Zap " + (amount / 1000).toString() + " sats to the DVM") // stringRes(id = R.string.donate_now))
        }
    }
}

@Composable
fun FeedEmptyWithStatus(
    appDefinitionNote: Note,
    status: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val card = observeAppDefinition(appDefinitionNote)

        card.cover?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(Size75dp)
                        .clip(QuoteBorder),
            )
        } ?: run { NoteAuthorPicture(appDefinitionNote, nav, accountViewModel, Size75dp) }

        Spacer(modifier = DoubleVertSpacer)

        Text(
            text = card.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = DoubleVertSpacer)

        Text(status)
    }
}

fun convertAppMetadataToCard(metadata: AppMetadata?): DVMCard {
    if (metadata == null) {
        return DVMCard(
            name = "",
            description = "",
            cover = null,
            amount = "",
            personalized = false,
        )
    }

    return with(metadata) {
        DVMCard(
            name = this.name ?: "",
            description = this.about ?: "",
            cover = this.profilePicture()?.ifBlank { null },
            amount = this.amount ?: "",
            personalized = this.personalized ?: false,
        )
    }
}

@Composable
fun observeAppDefinition(appDefinitionNote: Note): DVMCard {
    val noteEvent =
        appDefinitionNote.event as? AppDefinitionEvent ?: return DVMCard(
            name = "",
            description = "",
            cover = null,
            amount = "",
            personalized = false,
        )

    val card by
        appDefinitionNote
            .live()
            .metadata
            .map {
                convertAppMetadataToCard((it.note.event as? AppDefinitionEvent)?.appMetaData())
            }.distinctUntilChanged()
            .observeAsState(
                convertAppMetadataToCard(noteEvent.appMetaData()),
            )

    return card
}
