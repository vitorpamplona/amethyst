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
package com.vitorpamplona.amethyst.desktop.ui.relay.health

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.relays.health.ui.UnhealthyRelayBanner
import com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayHealthStore
import com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayListMutator

/**
 * Wraps the shared [UnhealthyRelayBanner] with desktop-specific count formatting
 * and the popup that opens when the banner is tapped. Renders nothing when the
 * health store is not provided (e.g. before login).
 */
@Composable
fun UnhealthyRelayBannerHost(
    onOpenDashboard: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val store = LocalRelayHealthStore.current ?: return
    val mutator = LocalRelayListMutator.current ?: return

    val unhealthy by store.unhealthy.collectAsState()
    val slowRelays by store.slowRelays.collectAsState()
    val countText by remember {
        derivedStateOf {
            val dead = unhealthy.size
            val slow = slowRelays.size
            when {
                dead == 0 && slow == 0 -> ""
                dead == 0 && slow == 1 -> "1 slow relay — Review"
                dead == 0 -> "$slow slow relays — Review"
                slow == 0 && dead == 1 -> "1 relay unresponsive — Review"
                slow == 0 -> "$dead relays unresponsive — Review"
                else -> "${dead + slow} relays need attention — Review"
            }
        }
    }

    var popupOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        UnhealthyRelayBanner(
            visible = unhealthy.isNotEmpty() || slowRelays.isNotEmpty(),
            text = countText,
            onClick = { popupOpen = true },
        )
        if (popupOpen) {
            UnhealthyRelaysPopup(
                store = store,
                mutator = mutator,
                onDismiss = { popupOpen = false },
                onOpenDashboard = onOpenDashboard,
                onShowMessage = onShowMessage,
            )
        }
    }
}
