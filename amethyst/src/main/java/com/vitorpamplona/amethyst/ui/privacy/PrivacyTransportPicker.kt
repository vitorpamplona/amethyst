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
package com.vitorpamplona.amethyst.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.privacy.PrivacyTransport
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import kotlinx.collections.immutable.persistentListOf

@Stable
class PrivacyTransportPickerViewModel : ViewModel() {
    val preferred = mutableStateOf(PrivacyTransport.DIRECT)

    fun reset(transport: PrivacyTransport) {
        preferred.value = transport
    }

    fun save(): PrivacyTransport = preferred.value
}

// Single picker for "which transport carries clearnet traffic". Hidden services
// (.onion / .i2p) are NOT affected by this choice — they always route through
// their matching daemon and fail closed when it's off. See PrivacyRouter.
@Composable
fun PreferredTransportPicker(viewModel: PrivacyTransportPickerViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 5.dp),
        verticalArrangement = Arrangement.spacedBy(Size10dp),
    ) {
        SettingsRow(
            R.string.privacy_clearnet_transport,
            R.string.privacy_clearnet_transport_explainer,
            persistentListOf(
                TitleExplainer(stringRes(R.string.privacy_clearnet_transport_direct)),
                TitleExplainer(stringRes(R.string.privacy_clearnet_transport_tor)),
                TitleExplainer(stringRes(R.string.privacy_clearnet_transport_i2p)),
            ),
            when (viewModel.preferred.value) {
                PrivacyTransport.DIRECT -> 0
                PrivacyTransport.TOR -> 1
                PrivacyTransport.I2P -> 2
            },
        ) { idx ->
            viewModel.preferred.value =
                when (idx) {
                    1 -> PrivacyTransport.TOR
                    2 -> PrivacyTransport.I2P
                    else -> PrivacyTransport.DIRECT
                }
        }
    }
}
