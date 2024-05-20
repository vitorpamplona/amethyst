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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.note.DVMCard
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.NostrNIP90ContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.events.NIP90StatusEvent

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
                    FeedEmptywithStatus(baseNote, stringResource(R.string.dvm_looking_for_app), accountViewModel, nav)
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
            FeedEmptywithStatus(appDefinition, stringResource(R.string.dvm_requesting_job), accountViewModel, nav)
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

    if (latestStatus != null) {
        // TODO: Make a good splash screen with loading animation for this DVM.
        latestStatus?.let {
            FeedEmptywithStatus(appDefinition, it.content(), accountViewModel, nav)
        }
    } else {
        // TODO: Make a good splash screen with loading animation for this DVM.
        FeedEmptywithStatus(appDefinition, stringResource(R.string.dvm_waiting_status), accountViewModel, nav)
    }
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
        // TODO (Optional) Maybe render a nice header with image and DVM name from the dvmID
        // TODO (Optional) How do we get the event information here?, LocalCache.checkGetOrCreateNote() returns note but event is empty
        // TODO (Optional) otherwise we have the NIP89 info in (note.event as AppDefinitionEvent).appMetaData()
        SaveableFeedState(resultFeedViewModel, null) { listState ->
            // TODO (Optional) Instead of a like reaction, do a Kind 31989 NIP89 App recommendation
            RenderFeedState(
                resultFeedViewModel,
                accountViewModel,
                listState,
                nav,
                null,
                onEmpty = {
                    // TODO (Optional) Maybe also show some dvm image/text while waiting for the notes in this custom component
                    FeedEmpty {
                        onRefresh()
                    }
                },
            )
        }
    }
}

@Composable
fun FeedEmptywithStatus(
    appDefinitionNote: Note,
    status: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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
