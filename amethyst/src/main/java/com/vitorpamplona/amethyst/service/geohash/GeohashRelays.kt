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
package com.vitorpamplona.amethyst.service.geohash

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayCsvLoader
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Process-wide geohash → relay directory, shared by everything that routes
 * geohash chat traffic (the joined-cell subscription, the chat screen). The live
 * CSV is fetched once via [ensureLoaded]; until then [closestRelays] falls back
 * to the small built-in list so routing works offline / on first run.
 */
object GeohashRelays {
    // The process-wide directory that GeohashChatChannel.relays() also reads, so a refresh
    // here immediately improves the relay set every geohash subscription resolves.
    private val directory = GeoRelayDirectory.shared

    @Volatile private var refreshed = false

    /** Fetches the live directory once. Safe to call repeatedly; subsequent calls are no-ops. */
    suspend fun ensureLoaded(): Boolean {
        if (refreshed) return false
        runCatching {
            GeoRelayCsvLoader { Amethyst.instance.okHttpClients.getHttpClient(false) }.refresh(directory)
        }
        val loaded = directory.size > GeoRelayDirectory.FALLBACK.size
        refreshed = loaded
        return loaded
    }

    /** The relays nearest [geohash]'s center. Synchronous — uses whatever is loaded (fallback if not yet refreshed). */
    fun closestRelays(geohash: String): List<NormalizedRelayUrl> = directory.closestRelays(geohash)
}
