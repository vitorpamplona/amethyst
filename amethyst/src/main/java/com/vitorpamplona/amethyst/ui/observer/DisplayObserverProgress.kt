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
package com.vitorpamplona.amethyst.ui.observer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.observer.ObserverPress
import com.vitorpamplona.amethyst.commons.observer.ui.ObserverPressBanner
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * App-wide floating banner for the Nostr Observer's press, mounted at the navigation root
 * beside the mining/broadcast banner so it survives navigation: progress while the paper
 * prints, then "Your paper is ready · Read". Sits one row higher while the mining/broadcast
 * banner is up, so the two stack instead of overlapping.
 */
@Composable
fun DisplayObserverProgress(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val press = accountViewModel.account.observerPress
    val state by press.state.collectAsStateWithLifecycle()
    val readerIsLooking by press.readerIsLooking.collectAsStateWithLifecycle()

    // Nothing has ever been printed: don't compose the overlay at all.
    if (state is ObserverPress.State.Idle) return

    val miningJobs by Amethyst.instance.powPublishQueue.jobs
        .collectAsStateWithLifecycle()
    val broadcasts by accountViewModel.broadcastTracker.activeBroadcasts.collectAsStateWithLifecycle()
    val stacked = miningJobs.isNotEmpty() || broadcasts.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        ObserverPressBanner(
            state = state,
            readerIsLooking = readerIsLooking,
            onOpen = {
                press.markSeen()
                nav.nav(Route.Observer)
            },
            onCancel = press::cancel,
            onDismiss = press::dismiss,
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = if (stacked) 130.dp else 58.dp)
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 560.dp),
        )
    }
}
