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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.OnchainZapStatus
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.dal.ThreadFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.delay

@Composable
fun ThreadScreen(
    noteId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (noteId == null) return

    val feedViewModel: ThreadFeedViewModel =
        viewModel(
            key = noteId + "NostrThreadFeedViewModel",
            factory = ThreadFeedViewModel.Factory(accountViewModel.account, noteId),
        )

    WatchLifecycleAndUpdateModel(feedViewModel)
    ThreadFilterAssemblerSubscription(noteId, accountViewModel)

    LoadNote(noteId, accountViewModel) {
        if (it != null) {
            // this will force loading every post from this thread.
            EventFinderFilterAssemblerSubscription(it, accountViewModel)
            ReverifyOnchainZapsWhileVisible(it, accountViewModel)
        }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(id = R.string.thread_title)) },
                popBack = nav::popBack,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        ThreadFeedView(noteId, feedViewModel, accountViewModel, nav)
    }
}

/**
 * Drives re-verification of NIP-BC onchain zaps while the thread is on screen.
 *
 * The chain backend often hasn't indexed a transaction at the moment its kind:8333
 * receipt is first consumed (especially for the user's own outgoing zap, broadcast
 * milliseconds earlier). We attach those entries optimistically as UNVERIFIED in
 * `LocalCache.consume(OnchainZapEvent)`; this composable re-runs the verifier on
 * view and then again whenever the chain tip advances, upgrading entries to
 * PENDING/CONFIRMED as the chain catches up.
 *
 * The polling interval is aligned with [com.vitorpamplona.quartz.nipBCOnchainZaps
 * .chain.CachingOnchainBackend]'s tip-height TTL — anything shorter would just hit
 * the cache and do no useful work.
 */
@Composable
private fun ReverifyOnchainZapsWhileVisible(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    LaunchedEffect(note) {
        val cache = accountViewModel.account.cache
        val backend = cache.onchainBackend ?: return@LaunchedEffect

        // First pass on view: covers entries attached optimistically just before navigation.
        cache.reverifyOnchainZapsForNote(note)

        var lastTip: Long? = null
        while (true) {
            val pending = note.onchainZaps.values.any { it.status != OnchainZapStatus.CONFIRMED }
            if (!pending) break

            val tip = runCatching { backend.tipHeight() }.getOrNull()
            if (tip != null && tip != lastTip) {
                lastTip = tip
                cache.reverifyOnchainZapsForNote(note)
            }
            delay(TIP_POLL_INTERVAL_MS)
        }
    }
}

private const val TIP_POLL_INTERVAL_MS = 60_000L
