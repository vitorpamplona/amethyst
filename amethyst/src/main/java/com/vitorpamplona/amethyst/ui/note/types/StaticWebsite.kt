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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.note.StaticWebsiteCard
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.napplet.NappletLauncher
import com.vitorpamplona.amethyst.napplethost.NappletBlobPrefetcher
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent

@Composable
fun RenderRootNappletEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RootNappletEvent ?: return
    val context = LocalContext.current

    PrefetchManifestBlobs(event.paths(), event.servers())

    StaticWebsiteCard(
        title = event.title(),
        description = event.description(),
        source = event.source(),
        servers = event.servers(),
        identifier = null,
        isNapplet = true,
        requires = event.requires(),
        icon = event.icon(),
        // Tapping Open hands off to the sandboxed :napplet process; the card itself never executes code.
        onOpen =
            if (event.paths().isNotEmpty()) {
                { NappletLauncher.launch(context = context, manifest = event, authorPubKey = event.pubKey, identifier = "") }
            } else {
                null
            },
    )
}

@Composable
fun RenderNamedNappletEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? NamedNappletEvent ?: return
    val context = LocalContext.current

    PrefetchManifestBlobs(event.paths(), event.servers())

    StaticWebsiteCard(
        title = event.title(),
        description = event.description(),
        source = event.source(),
        servers = event.servers(),
        identifier = event.identifier(),
        isNapplet = true,
        requires = event.requires(),
        icon = event.icon(),
        onOpen =
            if (event.paths().isNotEmpty()) {
                { NappletLauncher.launch(context = context, manifest = event, authorPubKey = event.pubKey, identifier = event.identifier()) }
            } else {
                null
            },
    )
}

@Composable
fun RenderRootSiteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RootSiteEvent ?: return
    val context = LocalContext.current

    PrefetchManifestBlobs(event.paths(), event.servers())

    StaticWebsiteCard(
        title = event.title(),
        description = event.description(),
        source = event.source(),
        servers = event.servers(),
        identifier = null,
        isNapplet = false,
        icon = event.icon(),
        onOpen =
            if (event.paths().isNotEmpty()) {
                {
                    NappletLauncher.launch(
                        context = context,
                        paths = event.paths(),
                        servers = event.servers(),
                        authorPubKey = event.pubKey,
                        identifier = "",
                        aggregateHash = null,
                        title = event.title() ?: "nsite",
                        requires = emptyList(),
                    )
                }
            } else {
                null
            },
    )
}

@Composable
fun RenderNamedSiteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? NamedSiteEvent ?: return
    val context = LocalContext.current

    PrefetchManifestBlobs(event.paths(), event.servers())

    StaticWebsiteCard(
        title = event.title(),
        description = event.description(),
        source = event.source(),
        servers = event.servers(),
        identifier = event.identifier(),
        isNapplet = false,
        icon = event.icon(),
        onOpen =
            if (event.paths().isNotEmpty()) {
                {
                    NappletLauncher.launch(
                        context = context,
                        paths = event.paths(),
                        servers = event.servers(),
                        authorPubKey = event.pubKey,
                        identifier = event.identifier(),
                        aggregateHash = null,
                        title = event.title() ?: event.identifier(),
                        requires = emptyList(),
                    )
                }
            } else {
                null
            },
    )
}

/**
 * While a static-site / napplet card is on screen, eagerly download + verify all of its blobs into the
 * shared content-addressed cache (Tor-routed), so tapping Open launches instantly. De-duplicated and
 * cancellation-aware — it stops when the card scrolls out of composition.
 */
@Composable
private fun PrefetchManifestBlobs(
    paths: List<PathTag>,
    servers: List<String>,
) {
    val context = LocalContext.current
    LaunchedEffect(paths, servers) {
        runCatching {
            val torPort = Amethyst.instance.torManager.activePortOrNull.value ?: -1
            NappletBlobPrefetcher.prefetch(paths, servers, context.cacheDir, torPort)
        }
    }
}
