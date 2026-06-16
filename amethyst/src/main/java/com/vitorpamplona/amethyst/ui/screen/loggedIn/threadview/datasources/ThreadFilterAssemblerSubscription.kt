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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Composable
fun ThreadFilterAssemblerSubscription(
    eventId: HexKey,
    accountViewModel: AccountViewModel,
) = ThreadFilterAssemblerSubscription(
    eventId,
    accountViewModel.account,
    accountViewModel.dataSources().thread,
)

@Composable
fun ThreadFilterAssemblerSubscription(
    eventId: HexKey,
    account: Account,
    filterAssembler: ThreadFilterAssembler,
) {
    // different screens get different states
    // even if they are tracking the same tag.
    val state =
        remember(eventId) {
            ThreadQueryState(eventId, account)
        }

    LifecycleAwareKeyDataSourceSubscription(state, filterAssembler)
}

/**
 * Eagerly pre-loads the whole thread of a reply that is visible in a feed.
 *
 * When a reply shows up in `NoteCompose`, this resolves the thread root and opens
 * the same root subscription the thread screen uses (a filter on the root's `e`/`a`
 * tag, covering NIP-10 and NIP-22 event/addressable roots), so tapping into the
 * conversation finds it already loaded. Keying on the resolved root id means every
 * visible reply that shares a root collapses onto a single subscription, and a note
 * that is itself a root (no parent) is skipped — nothing to pre-load.
 */
@Composable
fun PreloadThreadForReply(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val rootId =
        remember(note) {
            val root = ThreadAssembler(LocalCache).findRoot(note.idHex)
            if (root != null && root != note) root.idHex else null
        }

    if (rootId != null) {
        ThreadFilterAssemblerSubscription(rootId, accountViewModel)
    }
}
