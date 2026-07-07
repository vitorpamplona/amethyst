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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * A compact toggle to switch how the user's NIP-29 groups appear in the Messages
 * tab — [RelayGroupViewMode.INLINE] (channels as rows with a relay chip) vs
 * [RelayGroupViewMode.GROUPED] (a row per relay, drill in for its channels).
 * Only shown once the user has joined at least one group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayGroupViewModeToggle(accountViewModel: AccountViewModel) {
    val groups by accountViewModel.account.relayGroupList.liveRelayGroupList
        .collectAsStateWithLifecycle()
    if (groups.isEmpty()) return

    val mode by accountViewModel.account.settings.relayGroupViewMode
        .collectAsStateWithLifecycle()

    SingleChoiceSegmentedButtonRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        SegmentedButton(
            selected = mode == RelayGroupViewMode.INLINE,
            onClick = { accountViewModel.account.settings.updateRelayGroupViewMode(RelayGroupViewMode.INLINE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringRes(R.string.relay_group_view_inline))
        }
        SegmentedButton(
            selected = mode == RelayGroupViewMode.GROUPED,
            onClick = { accountViewModel.account.settings.updateRelayGroupViewMode(RelayGroupViewMode.GROUPED) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringRes(R.string.relay_group_view_grouped))
        }
    }
}
