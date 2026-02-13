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
import com.vitorpamplona.amethyst.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.service.broadcast.BroadcastTracker
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

/**
 * Displays broadcast progress UI components:
 * - BroadcastBanner: Shows active broadcasts with progress
 * - CompletedBroadcastIndicator: Shows completed broadcast for tap-to-view (auto-dismisses after 10s)
 * - BroadcastDetailsSheet: Shows detailed relay status on tap
 *
 * Only shown when FeatureSetType.COMPLETE is enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayBroadcastProgress(accountViewModel: AccountViewModel) {
    // Only show in COMPLETE UI mode
    if (!accountViewModel.settings.isCompleteUIMode()) return

    val activeBroadcasts by accountViewModel.broadcastTracker.activeBroadcasts.collectAsStateWithLifecycle()

    // State for details sheet
    var seeDetails by remember { mutableStateOf(false) }

    if (activeBroadcasts.isEmpty() && !seeDetails) return

    if (!seeDetails) {
        DisplaySnack(activeBroadcasts, { seeDetails = true }, accountViewModel)

        LaunchedEffect(activeBroadcasts) {
            // this effect gets restarted every time the active broadcast changes
            delay((BroadcastTracker.TIMEOUT_SECONDS + 1) * 1000)
            accountViewModel.broadcastTracker.clear()
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
    onTap: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BroadcastBanner(
            broadcasts = activeBroadcasts,
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
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 50.dp),
        )
    }
}
