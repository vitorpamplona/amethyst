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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.relays

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.RelayCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.ui.theme.DividerThickness

@Composable
fun RelayFeedView(
    viewModel: RelayFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val outboxListState by viewModel.nip65OutboxFlow.collectAsStateWithLifecycle()
    val inboxListState by viewModel.nip65InboxFlow.collectAsStateWithLifecycle()
    val dmListState by viewModel.dmInboxFlow.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
        state = rememberLazyListState(),
    ) {
        item {
            SettingsCategory(
                R.string.public_home_section,
                R.string.public_home_section_explainer_profile,
                Modifier.padding(top = 10.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
        itemsIndexed(outboxListState, key = { _, item -> "outbox" + item.url.url }) { _, item ->
            RenderRelayRow(item, accountViewModel, nav)
        }
        item {
            SettingsCategory(
                R.string.public_notif_section,
                R.string.public_notif_section_explainer_profile,
                Modifier.padding(top = 24.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
        itemsIndexed(inboxListState, key = { _, item -> "inbox" + item.url.url }) { _, item ->
            RenderRelayRow(item, accountViewModel, nav)
        }
        item {
            SettingsCategory(
                R.string.private_inbox_section,
                R.string.private_inbox_section_explainer_profile,
                Modifier.padding(top = 24.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
        itemsIndexed(dmListState, key = { _, item -> "dminbox" + item.url.url }) { _, item ->
            RenderRelayRow(item, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderRelayRow(
    relay: MyRelayInfo,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clipboardManager = LocalClipboardManager.current
    RelayCompose(
        relay,
        accountViewModel = accountViewModel,
        onAddRelay = {
            clipboardManager.setText(AnnotatedString(relay.url.url))
            nav.nav(Route.EditRelays)
        },
        onRemoveRelay = {
            nav.nav(Route.EditRelays)
        },
    )
    HorizontalDivider(
        thickness = DividerThickness,
    )
}
