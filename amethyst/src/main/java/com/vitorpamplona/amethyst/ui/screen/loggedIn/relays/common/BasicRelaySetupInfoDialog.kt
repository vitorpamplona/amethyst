/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.loadRelayInfo

@Composable
fun BasicRelaySetupInfoDialog(
    item: BasicRelaySetupInfo,
    onDelete: ((BasicRelaySetupInfo) -> Unit)?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relayInfo by loadRelayInfo(item.relay, accountViewModel)

    BasicRelaySetupInfoClickableRow(
        item = item,
        loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        onDelete = onDelete,
        accountViewModel = accountViewModel,
        onClick = { nav.nav(Route.RelayInfo(item.relay.url)) },
    )
}
