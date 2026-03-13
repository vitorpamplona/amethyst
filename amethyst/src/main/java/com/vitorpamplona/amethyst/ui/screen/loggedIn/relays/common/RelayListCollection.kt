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

import com.vitorpamplona.amethyst.R

data class RelayListCollection(
    val homeRelays: List<BasicRelaySetupInfo>,
    val notifRelays: List<BasicRelaySetupInfo>,
    val dmRelays: List<BasicRelaySetupInfo>,
    val privateOutboxRelays: List<BasicRelaySetupInfo>,
    val proxyRelays: List<BasicRelaySetupInfo>,
    val broadcastRelays: List<BasicRelaySetupInfo>,
    val indexerRelays: List<BasicRelaySetupInfo>,
    val searchRelays: List<BasicRelaySetupInfo>,
    val localRelays: List<BasicRelaySetupInfo>,
    val trustedRelays: List<BasicRelaySetupInfo>,
    val favoriteRelays: List<BasicRelaySetupInfo>,
    val blockedRelays: List<BasicRelaySetupInfo>,
) {
    fun sections(): List<RelaySection> =
        listOf(
            RelaySection("home", R.string.public_home_section, R.string.public_home_section_explainer, homeRelays),
            RelaySection("notifications", R.string.public_notif_section, R.string.public_notif_section_explainer, notifRelays),
            RelaySection("private_inbox", R.string.private_inbox_section, R.string.private_inbox_section_explainer, dmRelays),
            RelaySection("private_outbox", R.string.private_outbox_section, R.string.private_outbox_section_explainer, privateOutboxRelays),
            RelaySection("proxy", R.string.proxy_section, R.string.proxy_section_explainer, proxyRelays),
            RelaySection("broadcast", R.string.broadcast_section, R.string.broadcast_section_explainer, broadcastRelays),
            RelaySection("indexer", R.string.indexer_section, R.string.indexer_section_explainer, indexerRelays),
            RelaySection("search", R.string.search_section, R.string.search_section_explainer, searchRelays),
            RelaySection("local", R.string.local_section, R.string.local_section_explainer, localRelays),
            RelaySection("trusted", R.string.trusted_section, R.string.trusted_section_explainer, trustedRelays),
            RelaySection("favorites", R.string.favorite_section, R.string.favorite_section_explainer, favoriteRelays),
            RelaySection("blocked", R.string.blocked_section, R.string.blocked_section_explainer, blockedRelays),
        )
}

data class RelaySection(
    val fileName: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val relays: List<BasicRelaySetupInfo>,
)
