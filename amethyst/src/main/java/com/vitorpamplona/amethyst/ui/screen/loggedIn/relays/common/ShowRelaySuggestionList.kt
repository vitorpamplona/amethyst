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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import android.R.attr.onClick
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfVertPadding
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

@Composable
fun ShowRelaySuggestionList(
    relaySuggestions: IRelaySuggestionState,
    onSelect: (NormalizedRelayUrl) -> Unit,
    modifier: Modifier = Modifier,
    nip11CachedRetriever: Nip11CachedRetriever,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val suggestions by relaySuggestions.results.collectAsStateWithLifecycle(emptyList())

    if (suggestions.isNotEmpty()) {
        Column(modifier = modifier) {
            suggestions.forEachIndexed { index, relayInfo ->
                BasicRelaySetupInfoClickableRow(
                    item = relayInfo,
                    loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                    loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                    onClick = { onSelect(relayInfo.relay) },
                    onDelete = null,
                    nip11CachedRetriever = nip11CachedRetriever,
                    modifier = HalfVertPadding,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                if (index < suggestions.lastIndex) {
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        }
    }
}
