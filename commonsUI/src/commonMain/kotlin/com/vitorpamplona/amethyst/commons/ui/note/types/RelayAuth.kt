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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthTarget
import com.vitorpamplona.amethyst.commons.relayClient.auth.relayAuthTarget
import com.vitorpamplona.amethyst.commons.relayClient.auth.relayAuthTargets
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_auth_event_challenge
import com.vitorpamplona.amethyst.commons.resources.relay_auth_event_explainer
import com.vitorpamplona.amethyst.commons.resources.relay_auth_event_no_relay
import com.vitorpamplona.amethyst.commons.resources.relay_auth_event_title
import com.vitorpamplona.amethyst.commons.resources.relay_auth_event_unusual_relay
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelay
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

/**
 * NIP-42 kind 22242: a login to a relay. It is not a post — its content is empty and nobody but
 * the relay ever sees it — so it renders as "log in to <relay>" instead of falling through to the
 * text-note layout. Mostly met in signer consent prompts, where an app asks to sign one, so the
 * relay is shown exactly as the event names it (see [RelayAuthTarget]).
 */
@Composable
fun RenderRelayAuth(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayAuthEvent ?: return
    val relays = remember(noteEvent) { noteEvent.tags.relayAuthTargets() }
    val challenge = remember(noteEvent) { noteEvent.challenge() }

    RelayAuthCard(relays, challenge) { relay ->
        RenderRelay(relay, accountViewModel, nav)
    }
}

@Composable
fun RelayAuthCard(
    relays: List<RelayAuthTarget>,
    challenge: String?,
    relayIcon: @Composable (NormalizedRelayUrl) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Key,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = stringRes(Res.string.relay_auth_event_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringRes(Res.string.relay_auth_event_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (relays.isEmpty()) {
            Text(
                text = stringRes(Res.string.relay_auth_event_no_relay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 32.dp),
            )
        }

        relays.forEach { relay ->
            Column(modifier = Modifier.padding(start = 32.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val url = relay.url
                    if (url != null) {
                        relayIcon(url)
                    } else {
                        Icon(
                            symbol = MaterialSymbols.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    // Never ellipsized: the whole address is what the user is agreeing to.
                    Text(
                        text = relay.display,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (relay.unusual) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
                if (relay.unusual) {
                    Text(
                        text = stringRes(Res.string.relay_auth_event_unusual_relay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        challenge?.let {
            Text(
                text = stringRes(Res.string.relay_auth_event_challenge, it),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp),
            )
        }
    }
}

@Preview
@Composable
private fun RelayAuthCardPreview() {
    ThemeComparisonColumn {
        RelayAuthCard(
            relays = listOf(relayAuthTarget("wss://relay.damus.io"), relayAuthTarget("not a relay")),
            challenge = "4f2c9a1e-7b3d-4c8e-a6f0-2d9b1e3c5a7f",
        ) { relay ->
            RenderRelayIcon(
                displayUrl = relay.url,
                iconUrl = null,
                loadProfilePicture = false,
                loadRobohash = true,
                pingInMs = 0,
            )
        }
    }
}
