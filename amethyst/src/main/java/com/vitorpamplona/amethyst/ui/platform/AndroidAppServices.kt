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
package com.vitorpamplona.amethyst.ui.platform

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver

/**
 * [AppServices] over the main process's app modules. Every member is a getter, so installing
 * this object costs nothing and is safe in the `:napplet` process, where `Amethyst.instance` is
 * never built, as long as nothing there reads it.
 */
object AndroidAppServices : AppServices {
    override val favoriteApps: FavoriteAppsRegistry get() = Amethyst.instance.favoriteApps

    override val browserHistory: BrowserHistoryRegistry get() = Amethyst.instance.browserHistory

    override val browserIcons: BrowserIconRegistry get() = Amethyst.instance.browserIcons

    override val nappletPermissionLedger: NappletPermissionLedger get() = Amethyst.instance.nappletPermissionLedger

    override val signerPermissionStore: NostrSignerPermissionStore get() = Amethyst.instance.signerPermissionStore

    override val torSettings: TorSettingsFlow get() = Amethyst.instance.torPrefs.value

    override val namecoinResolver: NamecoinNameResolver get() = Amethyst.instance.namecoinResolver
}
