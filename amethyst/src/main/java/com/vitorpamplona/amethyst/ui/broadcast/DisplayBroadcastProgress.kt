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
package com.vitorpamplona.amethyst.ui.broadcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobState
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay

/**
 * Displays broadcast progress UI components:
 * - BroadcastBanner: Shows active broadcasts with progress
 * - CompletedBroadcastIndicator: Shows completed broadcast for tap-to-view (auto-dismisses after 10s)
 * - BroadcastDetailsSheet: Shows detailed relay status on tap
 *
 * The relay-progress part is hidden when the "Tracked broadcasts" UI setting
 * is off, but the NIP-13 mining phase always shows — the user needs to see
 * (and be able to cancel) posts still burning CPU in the queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayBroadcastProgress(accountViewModel: AccountViewModel) {
    val useTrackedBroadcasts by accountViewModel.settings.uiSettingsFlow.useTrackedBroadcasts
        .collectAsStateWithLifecycle()
    val trackingEnabled = useTrackedBroadcasts == BooleanType.ALWAYS

    val miningJobs by Amethyst.instance.powPublishQueue.jobs
        .collectAsStateWithLifecycle()
    val trackedBroadcasts by accountViewModel.broadcastTracker.activeBroadcasts.collectAsStateWithLifecycle()
    val activeBroadcasts = if (trackingEnabled) trackedBroadcasts else persistentListOf()

    // State for details sheet
    var seeDetails by remember { mutableStateOf(false) }

    if (activeBroadcasts.isEmpty() && miningJobs.isEmpty() && !seeDetails) return

    if (!seeDetails) {
        DisplaySnack(
            activeBroadcasts,
            miningJobs,
            { if (activeBroadcasts.isNotEmpty()) seeDetails = true },
            accountViewModel,
        )

        LaunchedEffect(activeBroadcasts) {
            // this effect gets restarted every time the active broadcast changes
            if (activeBroadcasts.isNotEmpty() && activeBroadcasts.all { it.isComplete }) {
                // All relays responded — dismiss quickly
                delay(3_000)
                accountViewModel.broadcastTracker.clear()
            }
        }
    } else {
        MultiBroadcastDetailsSheet(
            broadcasts = activeBroadcasts,
            onDismiss = {
                accountViewModel.runOnIO {
                    accountViewModel.broadcastTracker.clear()
                }
                seeDetails = false
            },
            onRetryRelay = { b: BroadcastEvent, relay: NormalizedRelayUrl ->
                accountViewModel.runOnIO {
                    accountViewModel.broadcastTracker.retry(
                        broadcast = b,
                        client = accountViewModel.account.client,
                        specificRelay = relay,
                    )
                }
            },
            onRetryAllFailed = { b: BroadcastEvent ->
                accountViewModel.runOnIO {
                    accountViewModel.broadcastTracker.retry(
                        broadcast = b,
                        client = accountViewModel.account.client,
                    )
                }
            },
        )
    }
}

@Composable
fun DisplaySnack(
    activeBroadcasts: ImmutableList<BroadcastEvent>,
    miningJobs: ImmutableList<PoWJobState>,
    onTap: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BroadcastBanner(
            broadcasts = activeBroadcasts,
            miningJobs = miningJobs,
            onCancelJob = { Amethyst.instance.powPublishQueue.cancel(it) },
            onSendWithoutPow = { Amethyst.instance.powPublishQueue.sendWithoutPow(it) },
            onTap = onTap,
            onRetryAll = {
                activeBroadcasts.forEach { b ->
                    accountViewModel.runOnIO {
                        accountViewModel.broadcastTracker.retry(
                            broadcast = b,
                            client = accountViewModel.account.client,
                        )
                    }
                }
            },
            onDismiss = {
                accountViewModel.broadcastTracker.clear()
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 58.dp)
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 560.dp),
        )
    }
}
