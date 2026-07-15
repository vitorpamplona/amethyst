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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

private sealed interface RedeemState {
    data object Working : RedeemState

    data class Done(
        val communityId: String,
    ) : RedeemState

    data object Failed : RedeemState
}

/**
 * Auto-redeems a Concord invite link (deep-link target for [Route.ConcordInvite]).
 * On open it fetches + unlocks the bundle, joins the community, and forwards to its
 * channel list. On failure it offers a retry, so a transient relay miss doesn't
 * strand the user.
 */
@Composable
fun ConcordInviteScreen(
    link: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var state by remember(link) { mutableStateOf<RedeemState>(RedeemState.Working) }

    LaunchedEffect(link, state) {
        if (state is RedeemState.Working) {
            val communityId = accountViewModel.account.joinConcordViaInvite(link)
            state = if (communityId != null) RedeemState.Done(communityId) else RedeemState.Failed
        }
    }

    LaunchedEffect(state) {
        (state as? RedeemState.Done)?.let { done ->
            nav.newStack(Route.ConcordServer(done.communityId))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is RedeemState.Working -> {
                CircularProgressIndicator()
                Text(
                    stringRes(com.vitorpamplona.amethyst.R.string.concord_redeeming_invite),
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }

            is RedeemState.Failed -> {
                Text(
                    stringRes(com.vitorpamplona.amethyst.R.string.concord_invite_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { state = RedeemState.Working },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringRes(com.vitorpamplona.amethyst.R.string.retry))
                }
            }

            is RedeemState.Done -> Unit
        }
    }
}
