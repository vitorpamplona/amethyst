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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunityHealth
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The community-wide status strip, directly under the app bar on a Concord community's screens.
 *
 * It exists because the states it reports explain **what you are seeing**, and the composer's slot at
 * the bottom is the wrong place to explain an empty feed. The sharpest case is stranding: a member
 * left out of a CORD-06 Refounding derives every plane address from a dead root, and a dead address
 * returns nothing rather than erroring — so the community renders as an ordinary quiet one, with no
 * affordance anywhere that could hint otherwise.
 *
 * One banner at a time, by construction: [ConcordCommunityHealth] is a single value rather than a set
 * of flags, so there is no way to stack two and turn this into chrome. [ConcordCommunityHealth.Healthy]
 * renders nothing — not an empty row, not padding.
 */
@Composable
fun ConcordCommunityBanner(
    communityId: String,
    accountViewModel: AccountViewModel,
) {
    val health by
        remember(communityId, accountViewModel) { accountViewModel.account.concordHealth.flowFor(communityId) }
            .collectAsStateWithLifecycle()

    when (val state = health) {
        ConcordCommunityHealth.Healthy -> Unit

        is ConcordCommunityHealth.Stranded ->
            Banner(
                // Recovery runs automatically on a timer, so while a way back exists the honest note is
                // that it is being handled. Without an anchor it is a dead end and says so.
                text =
                    if (state.recoverable) {
                        stringRes(R.string.concord_stranded_recovering)
                    } else {
                        stringRes(R.string.concord_stranded_no_anchor)
                    },
                symbol = MaterialSymbols.Key,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )

        is ConcordCommunityHealth.CatchingUp ->
            Banner(
                text = stringRes(R.string.concord_catching_up),
                symbol = null,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )

        is ConcordCommunityHealth.RecoveryFailed ->
            Banner(
                text =
                    stringRes(
                        when (state.reason) {
                            ConcordCommunityHealth.RecoveryFailed.Reason.LINK_REVOKED -> R.string.concord_recovery_failed_revoked
                            ConcordCommunityHealth.RecoveryFailed.Reason.LINK_EXPIRED -> R.string.concord_recovery_failed_expired
                            ConcordCommunityHealth.RecoveryFailed.Reason.LINK_UNREADABLE -> R.string.concord_recovery_failed_unreadable
                            ConcordCommunityHealth.RecoveryFailed.Reason.NO_ANCHOR -> R.string.concord_stranded_no_anchor
                        },
                    ),
                symbol = MaterialSymbols.ErrorOutline,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )

        ConcordCommunityHealth.Dissolved ->
            Banner(
                text = stringRes(R.string.concord_dissolved_banner),
                symbol = MaterialSymbols.Lock,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

/**
 * A full-width strip under the app bar. A null [symbol] renders a spinner instead — the one state that
 * is work in progress rather than a standing condition.
 */
@Composable
private fun Banner(
    text: String,
    symbol: MaterialSymbol?,
    container: Color,
    content: Color,
) {
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (symbol != null) {
                SymbolIcon(
                    symbol = symbol,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = content,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                )
            }
        }
    }
}
