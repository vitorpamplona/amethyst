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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

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
)

data class RelaySection(
    val fileName: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val relays: List<BasicRelaySetupInfo>,
)
