/**
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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import kotlinx.serialization.Serializable

@Serializable
data class ServerName(
    val name: String,
    val baseUrl: String,
    val type: ServerType = ServerType.NIP96,
)

enum class ServerType {
    NIP96,
    NIP95,
    Blossom,
}

val DEFAULT_MEDIA_SERVERS: List<ServerName> =
    listOf(
        ServerName("Nostr.Build", "https://nostr.build", ServerType.NIP96),
        ServerName("NostrCheck.me (NIP-96)", "https://nostrcheck.me", ServerType.NIP96),
        ServerName("Sovbit", "https://files.sovbit.host", ServerType.NIP96),
        ServerName("Satellite (Paid)", "https://cdn.satellite.earth", ServerType.Blossom),
        ServerName("NostrCheck.me (Blossom)", "https://cdn.nostrcheck.me", ServerType.Blossom),
        ServerName("Nostr.Download", "https://nostr.download", ServerType.Blossom),
        ServerName("NostrMedia (Paid)", "https://nostrmedia.com", ServerType.Blossom),
    )
