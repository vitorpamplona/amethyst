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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_open_failed
import com.vitorpamplona.amethyst.commons.resources.nsite_open_failed
import com.vitorpamplona.amethyst.commons.ui.note.NappletCardInfo
import com.vitorpamplona.amethyst.commons.ui.note.StaticWebsiteCard
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.platform.rememberNappletIconModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.utils.Log

/** The desktop's napplet host, for the note cards; null until the app provides it. */
val LocalDesktopNappletHost = compositionLocalOf<DesktopNappletHost?> { null }

/** Opens [manifest], and tells the user (with the reason in the log) when it can't. */
fun openNapplet(
    host: DesktopNappletHost?,
    manifest: NappletManifest,
    toaster: Toaster,
    openFailed: String,
) {
    if (host == null) return
    host.open(manifest).onFailure {
        Log.w("DesktopNappletHost", "Refusing to open napplet ${(manifest as? Event)?.id}", it)
        toaster.show(openFailed, false)
    }
}

/**
 * The shared napplet card with an Open button that runs it in [DesktopNappletHost]. The card itself
 * never runs the napplet's code; Open is offered only for a manifest this host can launch.
 */
@Composable
fun DesktopNappletCard(
    event: NappletManifest,
    identifier: String?,
) {
    val host = LocalDesktopNappletHost.current
    val info = remember(event) { NappletCardInfo(event) }
    val coordinate = remember(event) { favoriteCoordinateOf(event as Event) }
    val toaster = LocalAppPlatform.current.rememberToaster()
    val openFailed = stringRes(Res.string.napplet_desktop_open_failed)
    StaticWebsiteCard(
        title = info.title,
        description = info.description,
        source = info.source,
        servers = info.servers,
        identifier = identifier,
        isNapplet = true,
        requires = info.required,
        icon = rememberNappletIconModel(coordinate),
        onOpen = if (info.launchable && host != null) ({ openNapplet(host, event, toaster, openFailed) }) else null,
    )
}

/** Opens the nSite [event], and tells the user (with the reason in the log) when it can't. */
fun openSite(
    host: DesktopNappletHost?,
    event: Event,
    toaster: Toaster,
    openFailed: String,
) {
    if (host == null) return
    host.openSite(event).onFailure {
        Log.w("DesktopNappletHost", "Refusing to open nSite ${event.id}", it)
        toaster.show(openFailed, false)
    }
}

/**
 * The shared nSite card with an Open button that runs the site in [DesktopNappletHost], in the
 * website posture. The card itself never runs the site's code.
 */
@Composable
fun DesktopSiteCard(event: Event) {
    val host = LocalDesktopNappletHost.current
    val toaster = LocalAppPlatform.current.rememberToaster()
    val openFailed = stringRes(Res.string.nsite_open_failed)
    // Parsed once per event, not on every recomposition of the feed.
    val hasFiles = remember(event) { (event as? RootSiteEvent)?.paths()?.isNotEmpty() ?: (event as? NamedSiteEvent)?.paths()?.isNotEmpty() ?: false }
    val launchable = host != null && hasFiles
    val onOpen = if (launchable) ({ openSite(host, event, toaster, openFailed) }) else null
    when (event) {
        is RootSiteEvent -> StaticWebsiteCard(event.title(), event.description(), event.source(), event.servers(), null, isNapplet = false, icon = event.icon(), onOpen = onOpen)
        is NamedSiteEvent -> StaticWebsiteCard(event.title(), event.description(), event.source(), event.servers(), event.identifier(), isNapplet = false, icon = event.icon(), onOpen = onOpen)
    }
}
