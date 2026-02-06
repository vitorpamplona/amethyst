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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.ReusableZapButton
import com.vitorpamplona.amethyst.ui.components.ZapButtonConfig
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.DVMCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.dal.NIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.SimpleImage75Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppMetadata
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent

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
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            LoadNote(baseNoteHex = appDefinitionEventId, accountViewModel = accountViewModel) { note ->
                note?.let { baseNote ->
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
        accountViewModel.requestDVMContentDiscovery(noteAuthor) {
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

    EventFinderFilterAssemblerSubscription(dvmRequestId, accountViewModel)

    val resultFlow =
        remember(dvmRequestId) {
            accountViewModel.account.cache
                .observeLatestEvent<NIP90ContentDiscoveryResponseEvent>(
                    Filter(
                        kinds = listOf(NIP90ContentDiscoveryResponseEvent.KIND),
                        tags = mapOf("e" to listOf(dvmRequestId.idHex)),
                        limit = 1,
                    ),
                )
        }

    val latestResponse by resultFlow.collectAsStateWithLifecycle(null)
    val myResponse = latestResponse

    if (myResponse != null) {
        PrepareViewContentDiscoveryModels(
            noteAuthor,
            dvmRequestId.idHex,
            myResponse,
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
            accountViewModel.account.cache
                .observeLatestEvent<NIP90StatusEvent>(
                    Filter(
                        kinds = listOf(NIP90StatusEvent.KIND),
                        tags = mapOf("e" to listOf(dvmRequestId)),
                        limit = 1,
                    ),
                )
        }

    val latestStatus by statusFlow.collectAsStateWithLifecycle(null)

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
    latestResponse: NIP90ContentDiscoveryResponseEvent,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val resultFeedViewModel: NIP90ContentDiscoveryFeedViewModel =
        viewModel(
            key = "NostrNIP90ContentDiscoveryFeedViewModel${dvm.pubkeyHex}$dvmRequestId",
            factory = NIP90ContentDiscoveryFeedViewModel.Factory(accountViewModel.account, dvmKey = dvm.pubkeyHex, requestId = dvmRequestId),
        )

    LaunchedEffect(key1 = dvmRequestId, latestResponse.id) {
        resultFeedViewModel.invalidateData()
    }

    RenderNostrNIP90ContentDiscoveryScreen(resultFeedViewModel, onRefresh, accountViewModel, nav)
}

@Composable
fun RenderNostrNIP90ContentDiscoveryScreen(
    resultFeedViewModel: NIP90ContentDiscoveryFeedViewModel,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.fillMaxHeight()) {
        SaveableFeedState(resultFeedViewModel.feedState, null) { listState ->
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
        val card = observeAppDefinition(appDefinitionNote, accountViewModel)

        card.cover?.let {
            Box(contentAlignment = BottomStart) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = SimpleImage75Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                    onError = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                )
            }
        } ?: run {
            appDefinitionNote.author?.let { author ->
                Box(contentAlignment = BottomStart) {
                    BannerImage(author, SimpleImage75Modifier, accountViewModel)
                }
            }
        }

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
                    if (accountViewModel.account.nip47SignerState.hasWalletConnectSetup()) {
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
                        } catch (_: Exception) {
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
    nav: INav,
) {
    val config =
        ZapButtonConfig(
            grayTint = grayTint,
            iconSize = iconSize,
            showUserFinderSubscription = true,
            zapAmountChoices = listOf(amount / 1000),
            buttonText = "Zap ${(amount / 1000)} sats to the DVM",
        )

    ReusableZapButton(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
        config = config,
    )
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
        val card = observeAppDefinition(appDefinitionNote, accountViewModel)

        card.cover?.let {
            Box(contentAlignment = BottomStart) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = SimpleImage75Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                    onError = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                )
            }
        } ?: run {
            appDefinitionNote.author?.let { author ->
                Box(contentAlignment = BottomStart) {
                    BannerImage(author, SimpleImage75Modifier, accountViewModel)
                }
            }
        }

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
fun observeAppDefinition(
    appDefinitionNote: Note,
    accountViewModel: AccountViewModel,
): DVMCard {
    val card by observeNoteAndMap(appDefinitionNote, accountViewModel) {
        convertAppMetadataToCard((it.event as? AppDefinitionEvent)?.appMetaData())
    }
    return card
}
