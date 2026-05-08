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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.ui.components.BunkerHeartbeatIndicator
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.platform.titleBarInsetTop
import com.vitorpamplona.amethyst.desktop.ui.account.AccountSwitcherDropdown
import com.vitorpamplona.amethyst.desktop.ui.tor.TorStatusIndicator
import kotlinx.collections.immutable.ImmutableList

@Composable
fun DeckSidebar(
    activeNpub: String?,
    allAccounts: ImmutableList<AccountInfo>,
    localCache: DesktopLocalCache?,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddColumn: () -> Unit,
    onOpenSettings: () -> Unit,
    signerConnectionState: SignerConnectionState,
    lastPingTimeSec: Long?,
    torStatus: TorServiceStatus,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(top = 8.dp + titleBarInsetTop, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        AccountSwitcherDropdown(
            activeNpub = activeNpub,
            allAccounts = allAccounts,
            localCache = localCache,
            onSwitchAccount = onSwitchAccount,
            onAddAccount = onAddAccount,
            onRemoveAccount = onRemoveAccount,
        )

        Spacer(Modifier.size(16.dp))

        IconButton(onClick = onAddColumn) {
            Icon(
                MaterialSymbols.Add,
                contentDescription = "Add Column",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        BunkerHeartbeatIndicator(
            signerConnectionState = signerConnectionState,
            lastPingTimeSec = lastPingTimeSec,
        )

        Spacer(Modifier.size(4.dp))
        TorStatusIndicator(status = torStatus, onClick = onOpenSettings)

        Spacer(Modifier.size(4.dp))

        IconButton(onClick = onOpenSettings) {
            Icon(
                MaterialSymbols.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
