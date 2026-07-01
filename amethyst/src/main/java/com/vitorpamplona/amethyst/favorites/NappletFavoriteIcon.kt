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
package com.vitorpamplona.amethyst.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.napplethost.NappletBlobCache
import com.vitorpamplona.amethyst.napplethost.NappletBlobPrefetcher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The chosen icon blob and the servers that hold it — resolved from the live manifest. */
private class IconBlob(
    val path: PathTag,
    val servers: List<String>,
)

/**
 * A Coil model (`file://…`) for the app icon an nsite/napplet bundles in its own content — the verified,
 * content-addressed blob the [NappletIconPath][com.vitorpamplona.quartz.nip5aStaticWebsites.NappletIconPath]
 * heuristic picks from the manifest's `path` tags — or null when the manifest declares no such icon (or it
 * hasn't downloaded yet). Used to decorate an nsite/napplet favorite the same way a captured favicon
 * decorates a web favorite, except this rides the same Tor-routed, sha256-verified blob path as the rest of
 * the site instead of a clearnet favicon fetch.
 *
 * Unlike a webapp's favicon, this can't be captured live: the applet runs in a cross-origin sandboxed
 * iframe under the trusted shell, so `WebChromeClient.onReceivedIcon` only ever reports the shell's icon,
 * never the applet's. Deriving it from the bundled blobs is the only path that sees the real icon.
 *
 * Observes the addressable note, so the icon resolves whenever the manifest arrives or updates in
 * LocalCache — not only if it already happened to be cached at first composition (on a cold start the
 * event streams in from relays a moment later). Returns null until the blob is on disk; the icon then
 * appears on the next recomposition. All disk + network work runs off the composition thread. The blob is
 * usually already cached (the browse/feed card prefetches every manifest blob, this one included); the
 * on-demand fetch here just covers favorites whose card isn't currently on screen.
 */
@Composable
fun rememberNappletIconModel(coordinate: String): String? {
    val context = LocalContext.current

    // checkGetOrCreate returns null only for a malformed coordinate, so this early return is stable for a
    // given coordinate (it never flips across recompositions, which would break composition structure).
    val note = remember(coordinate) { LocalCache.checkGetOrCreateAddressableNote(coordinate) } ?: return null
    val noteState by note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    // Key on the event itself, not the NoteState wrapper: re-resolve only when the manifest actually
    // changes, not on every unrelated metadata bump (a reaction/zap tracked on the note).
    val event = noteState.note.event
    val icon = remember(event) { resolveIconBlob(event) } ?: return null

    var model by remember(icon.path.hash) { mutableStateOf<String?>(null) }
    LaunchedEffect(icon.path.hash) {
        withContext(Dispatchers.IO) {
            val file = File(NappletBlobCache.dirFor(context.cacheDir), icon.path.hash.lowercase())
            if (!file.isFile) {
                val torPort = Amethyst.instance.torManager.activePortOrNull.value ?: -1
                runCatching { NappletBlobPrefetcher.prefetch(listOf(icon.path), icon.servers, context.cacheDir, torPort) }
            }
            if (file.isFile) model = "file://" + file.absolutePath
        }
    }
    return model
}

/** Asks each nsite/napplet event type for its bundled icon blob + the servers that hold it. */
private fun resolveIconBlob(event: Event?): IconBlob? =
    when (event) {
        is RootNappletEvent -> event.iconBlob()?.let { IconBlob(it, event.servers()) }
        is NamedNappletEvent -> event.iconBlob()?.let { IconBlob(it, event.servers()) }
        is RootSiteEvent -> event.iconBlob()?.let { IconBlob(it, event.servers()) }
        is NamedSiteEvent -> event.iconBlob()?.let { IconBlob(it, event.servers()) }
        else -> null
    }

/**
 * A Coil model (`file://…`) for the cached favicon of [url]'s host, or null when no favicon
 * has been captured yet. The favicon is stored by [BrowserIconRegistry] at browse time (the
 * WebView captures it in the sandboxed `:napplet` process); this composable just reads the cache.
 *
 * Early-returns null when [url] is blank or has no parseable host — this early return is stable
 * for a given [url] (the host either always parses or never does), so composition structure is
 * preserved across recompositions.
 */
@Composable
fun rememberWebAppIconModel(url: String): String? {
    val host = remember(url) { OmniboxInput.hostOf(url) } ?: return null
    val iconKeys by BrowserIconRegistry.keys.collectAsStateWithLifecycle()
    return remember(host, iconKeys) { BrowserIconRegistry.iconModelFor(host) }
}

/**
 * A Coil model for the bundled icon of an napplet or nsite identified by [author] and
 * [identifier]. Tries the napplet manifest (kinds 15129 / 35129) first, then falls back to the
 * nsite manifest (kinds 15128 / 35128). Returns null until the blob lands on disk.
 */
@Composable
fun rememberManifestIconModel(
    author: String,
    identifier: String,
): String? {
    val nappletCoord =
        remember(author, identifier) {
            if (identifier.isEmpty()) "${RootNappletEvent.KIND}:$author:" else "${NamedNappletEvent.KIND}:$author:$identifier"
        }
    val nsiteCoord =
        remember(author, identifier) {
            if (identifier.isEmpty()) "${RootSiteEvent.KIND}:$author:" else "${NamedSiteEvent.KIND}:$author:$identifier"
        }
    return rememberNappletIconModel(nappletCoord) ?: rememberNappletIconModel(nsiteCoord)
}
