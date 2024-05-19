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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.note.DVMCard
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.ObserveZapIcon
import com.vitorpamplona.amethyst.ui.note.PayViaIntentDialog
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoicePopup
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.note.elements.customZapClick
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.events.NIP90StatusEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NIP90ContentDiscoveryScreen(
    appDefinitionEventId: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadNote(baseNoteHex = appDefinitionEventId, accountViewModel = accountViewModel) {
        it?.let { baseNote ->
            WatchNoteEvent(
                baseNote,
                onNoteEventFound = {
                    NIP90ContentDiscoveryScreen(baseNote, accountViewModel, nav)
                },
                onBlank = {
                    FeedDVM(baseNote, null, accountViewModel, nav)
                },
            )
        }
    }
}

@Composable
fun NIP90ContentDiscoveryScreen(
    appDefinition: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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
        onRefresh()
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
            FeedDVM(appDefinition, null, accountViewModel, nav)
        }
    }
}

@Composable
fun ObserverContentDiscoveryResponse(
    appDefinition: Note,
    dvmRequestId: Note,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteAuthor = appDefinition.author ?: return
    val updateFiltersFromRelays = dvmRequestId.live().metadata.observeAsState()

    val resultFlow =
        remember(dvmRequestId) {
            accountViewModel.observeByETag(NIP90ContentDiscoveryResponseEvent.KIND, dvmRequestId.idHex)
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
    nav: (String) -> Unit,
) {
    val statusFlow =
        remember(dvmRequestId) {
            accountViewModel.observeByETag(NIP90StatusEvent.KIND, dvmRequestId)
        }

    val latestStatus by statusFlow.collectAsStateWithLifecycle()
    // TODO: Make a good splash screen with loading animation for this DVM.
    FeedDVM(appDefinition, latestStatus, accountViewModel, nav)
}

@Composable
fun PrepareViewContentDiscoveryModels(
    dvm: User,
    dvmRequestId: String,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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
    nav: (String) -> Unit,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedDVM(
    appDefinitionNote: Note,
    latestStatus: Event?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var status = "waiting"
    var content = ""
    var lninvoice = ""
    var amount: Long = 0
    var statusNote: Note? = null
    var dvmUser: User? = null

    if (latestStatus == null) {
        content = stringResource(R.string.dvm_waiting_status)
    } else {
        latestStatus.let {
            LoadNote(baseNoteHex = it.id, accountViewModel = accountViewModel) { stateNote ->
                if (stateNote != null) {
                    statusNote = stateNote
                }
            }
            content = it.content()

            val statusTag =
                it.tags().first { it2 ->
                    it2.size > 1 && (it2[0] == "status")
                }
            status = statusTag[1]

            if (statusTag.size > 2 && content == "") {
                // Some DVMs *might* send a the content in the second status tag (even though the NIP says otherwise)
                content = statusTag[2]
            }

            if (status == "payment-required") {
                val amountTag =
                    it.tags().first { it2 ->
                        it2.size > 1 && (it2[0] == "amount")
                    }
                amount = amountTag[1].toLong()

                if (amountTag.size > 2) {
                    // DVM *might* send a lninvoice in the second tag
                    lninvoice = amountTag[2]
                }
            }
        }
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
        Text(content, textAlign = TextAlign.Center)

        if (status == "payment-required") {
            if (lninvoice != "") {
                val context = LocalContext.current
                // TODO is there a better function?
                Button(onClick = {
                    payViaIntent(
                        lninvoice,
                        context,
                        onPaid = { println("paid") },
                        onError = { println("error") },
                    )
                }) {
                    Text(text = "Pay     " + (amount / 1000).toString() + " Sats to the DVM")
                }
            } else {
                statusNote?.let {
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
    nav: (String) -> Unit,
) {
    var wantsToZap by remember { mutableStateOf<List<Long>?>(null) }
    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }
    var wantsToPay by
        remember(baseNote) {
            mutableStateOf<ImmutableList<ZapPaymentHandler.Payable>>(
                persistentListOf(),
            )
        }
    baseNote.author?.let {
        LoadUser(baseUserHex = it.pubkeyHex, accountViewModel = accountViewModel) { author ->
            if (author != null) {
                author.live().metadata.observeAsState()
                println(author.info?.lnAddress())
                println(author.info?.name)
            }
        }
    }

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
                zapAmountChoices = listOf(amount / 1000),
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
                title = stringResource(id = R.string.error_dialog_zap_error),
                textContent = showErrorMessageDialog ?: "",
                onClickStartMessage = {
                    baseNote.author?.let {
                        scope.launch(Dispatchers.IO) {
                            val route = routeToMessage(it, showErrorMessageDialog, accountViewModel)
                            nav(route)
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
                        )
                            .value,
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

                    Crossfade(targetState = wasZappedByLoggedInUser.value, label = "ZapIcon") {
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
            Text(text = stringResource(id = R.string.thank_you))
        } else {
            Text(text = "Zap " + (amount / 1000).toString() + " Sats to the DVM") // stringResource(id = R.string.donate_now))
        }
    }
}

@Composable
fun observeAppDefinition(appDefinitionNote: Note): DVMCard {
    val noteEvent =
        appDefinitionNote.event as? AppDefinitionEvent ?: return DVMCard(
            name = "",
            description = "",
            cover = null,
        )

    val card by
        appDefinitionNote
            .live()
            .metadata
            .map {
                val noteEvent = it.note.event as? AppDefinitionEvent

                DVMCard(
                    name = noteEvent?.appMetaData()?.name ?: "",
                    description = noteEvent?.appMetaData()?.about ?: "",
                    cover = noteEvent?.appMetaData()?.image?.ifBlank { null },
                )
            }
            .distinctUntilChanged()
            .observeAsState(
                DVMCard(
                    name = noteEvent.appMetaData()?.name ?: "",
                    description = noteEvent.appMetaData()?.about ?: "",
                    cover = noteEvent.appMetaData()?.image?.ifBlank { null },
                ),
            )

    return card
}
