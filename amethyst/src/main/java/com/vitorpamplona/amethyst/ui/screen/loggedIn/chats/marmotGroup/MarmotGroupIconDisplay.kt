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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupImage
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageCipher
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri

/**
 * Resolve the URL from which a Marmot group's encrypted avatar can be loaded, and
 * register its decryption cipher in the encrypted-blob HTTP cache so any Coil load
 * of that URL transparently yields the decrypted image (via `EncryptedBlobInterceptor`).
 *
 * The blob is content-addressed on Blossom by [MarmotGroupImage.hash]; the canonical
 * scheme stores only the hash, and the icon usually lives on the *uploader's* Blossom
 * server rather than the viewer's. So we resolve it through [Amethyst.blossomResolver],
 * which probes the viewer's default server (passed as a first-try `xs` hint) and the
 * group admins' configured servers (via their pubkeys as `as` authors, BUD-03). While
 * that async probe runs, we optimistically load from the viewer's default server so the
 * common case (shared server) shows instantly; if nothing resolves, callers fall back to
 * the relay icon.
 *
 * Returns null when there is no image to show.
 */
@Composable
fun rememberMarmotGroupIconUrl(
    image: MarmotGroupImage?,
    accountViewModel: AccountViewModel,
    adminPubkeys: List<HexKey> = emptyList(),
): String? {
    if (image == null) return null

    val serverBaseUrl = accountViewModel.account.settings.defaultFileServer.baseUrl
    val fallbackUrl = remember(image.hash, serverBaseUrl) { BlossomServerUrl.blob(serverBaseUrl, image.hash) }

    // A blossom: URI carrying the default server as a first-try hint and the admins as
    // authors. Extension "bin" keeps the resolver's HEAD check type-agnostic, matching the
    // application/octet-stream encrypted blob.
    val blossomUri =
        remember(image.hash, serverBaseUrl, adminPubkeys) {
            BlossomUri(
                sha256 = image.hash,
                extension = "bin",
                servers = listOf(serverBaseUrl),
                authors = adminPubkeys,
                size = null,
            ).toUriString()
        }

    val resolver = Amethyst.instance.blossomResolver
    val url by produceState(resolver.cachedFindServer(blossomUri)?.serverUrl ?: fallbackUrl, blossomUri) {
        value = resolver.findServers(blossomUri)?.serverUrl ?: fallbackUrl
    }

    val cipher = remember(image) { MarmotGroupImageCipher(image.key, image.nonce, image.mediaType) }
    // Register the cipher only when the URL or cipher changes (not on every recomposition),
    // and synchronously during composition so the interceptor can decrypt before Coil fetches.
    remember(url, cipher) {
        Amethyst.instance.keyCache.add(url, cipher, image.mediaType)
        url
    }

    return url
}

/**
 * The NIP-11 icon of the group's first resolvable relay, used as a fallback avatar
 * when the group has no image of its own. Fetches the relay's NIP-11 document on a
 * cache miss. Returns null when the group has no valid relay or the relay advertises
 * no icon.
 */
@Composable
fun loadMarmotRelayIcon(relays: List<String>): String? {
    val relay = remember(relays) { relays.firstNotNullOfOrNull { RelayUrlNormalizer.normalizeOrNull(it) } } ?: return null
    val relayInfo by loadRelayInfo(relay)
    return relayInfo.icon?.ifBlank { null }
}
