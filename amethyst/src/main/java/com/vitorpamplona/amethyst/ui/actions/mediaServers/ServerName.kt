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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import kotlinx.serialization.Serializable

@Serializable
data class ServerName(
    val name: String,
    val baseUrl: String,
    val type: ServerType = ServerType.Blossom,
)

enum class ServerType {
    Blossom,
    NIP95,
    NIP96,
}

val DEFAULT_MEDIA_SERVERS: List<ServerName> =
    listOf(
        ServerName("Nostr.Build", "https://blossom.band/", ServerType.Blossom),
        ServerName("24242.io", "https://24242.io/", ServerType.Blossom),
        ServerName("Azzamo", "https://blossom.azzamo.media", ServerType.Blossom),
        ServerName("YakiHonne", "https://blossom.yakihonne.com/", ServerType.Blossom),
        ServerName("Primal", "https://blossom.primal.net/", ServerType.Blossom),
        ServerName("Sovbit", "https://cdn.sovbit.host", ServerType.Blossom),
        ServerName("Nostr.Download", "https://nostr.download", ServerType.Blossom),
        ServerName("Satellite (Paid)", "https://cdn.satellite.earth", ServerType.Blossom),
        ServerName("NostrMedia (Paid)", "https://nostrmedia.com", ServerType.Blossom),
    )
