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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Lifecycle-aware subscription for the per-room kind-10312 presence
 * stream. Mirror of [ThreadFilterAssemblerSubscription] — call this
 * from the room screen and the relay sub stays open while the
 * Composable is in the tree, then closes on dispose.
 */
@Composable
fun RoomPresenceFilterAssemblerSubscription(
    roomATag: String,
    accountViewModel: AccountViewModel,
) = RoomPresenceFilterAssemblerSubscription(
    roomATag,
    accountViewModel.account,
    accountViewModel.dataSources().roomPresence,
)

@Composable
fun RoomPresenceFilterAssemblerSubscription(
    roomATag: String,
    account: Account,
    filterAssembler: RoomPresenceFilterAssembler,
) {
    val state = remember(roomATag) { RoomPresenceQueryState(roomATag, account) }
    KeyDataSourceSubscription(state, filterAssembler)
}
