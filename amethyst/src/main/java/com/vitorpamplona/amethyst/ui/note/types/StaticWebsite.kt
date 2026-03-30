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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.ui.theme.previewCardImageModifier
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent

@Composable
fun RenderRootSiteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RootSiteEvent ?: return

    val npub =
        remember(event.pubKey) {
            event.pubKey.hexToByteArray().toNpub()
        }

    RenderStaticWebsitePreview(
        title = event.title(),
        description = event.description(),
        npub = npub,
        identifier = null,
        faviconHash = event.paths().firstOrNull { it.path == "/favicon.ico" }?.hash,
        servers = event.servers(),
    )
}

@Composable
fun RenderNamedSiteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? NamedSiteEvent ?: return

    val npub =
        remember(event.pubKey) {
            event.pubKey.hexToByteArray().toNpub()
        }

    RenderStaticWebsitePreview(
        title = event.title(),
        description = event.description(),
        npub = npub,
        identifier = event.identifier(),
        faviconHash = event.paths().firstOrNull { it.path == "/favicon.ico" }?.hash,
        servers = event.servers(),
    )
}

@Composable
private fun RenderStaticWebsitePreview(
    title: String?,
    description: String?,
    npub: String,
    identifier: String?,
    faviconHash: String?,
    servers: List<String>,
) {
    val nsiteHost =
        if (identifier != null) {
            "$npub.$identifier.nsite.lol"
        } else {
            "$npub.nsite.lol"
        }

    val nsiteUrl = "https://$nsiteHost"

    val faviconUrl =
        if (faviconHash != null && servers.isNotEmpty()) {
            "${servers.first()}/$faviconHash"
        } else {
            null
        }

    val uri = LocalUriHandler.current

    Column(
        modifier = MaterialTheme.colorScheme.innerPostModifier,
    ) {
        faviconUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = title,
                modifier = previewCardImageModifier,
            )
        }

        Text(
            text = nsiteHost,
            style = MaterialTheme.typography.bodySmall,
            modifier = MaxWidthWithHorzPadding,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = title ?: identifier ?: stringRes(id = R.string.nsite_root_site),
            style = MaterialTheme.typography.bodyMedium,
            modifier = MaxWidthWithHorzPadding,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier = MaxWidthWithHorzPadding,
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = DoubleVertSpacer)
    }
}
