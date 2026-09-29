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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver

/**
 * App-wide stores that outlive any one account and that screens read directly: the favorites
 * row, the browser's history and favicons, what connected apps may do, the Tor settings and the
 * Namecoin resolver.
 * Account-scoped services reach screens through the account's ViewModel instead.
 *
 * A front end implements it over its own modules; shared UI reads it through `LocalAppServices`.
 */
interface AppServices {
    val favoriteApps: FavoriteAppsRegistry

    val browserHistory: BrowserHistoryRegistry

    val browserIcons: BrowserIconRegistry

    /** What each napplet/nsite has been granted, per account. */
    val nappletPermissionLedger: NappletPermissionLedger

    /** What each remote-signer client has been granted. */
    val signerPermissionStore: NostrSignerPermissionStore

    val torSettings: TorSettingsFlow

    /** Resolves `.bit` names and `d/`/`id/` identifiers over the configured ElectrumX servers. */
    val namecoinResolver: NamecoinNameResolver
}
