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
package com.vitorpamplona.amethyst.commons.llamagarden

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a listing's `.torrent` mirrors (`url`) hold against its signed hash (`x`). llama.garden's viewer
 * makes this check before it lets a `.torrent` or an `xs=` exact source reach the BitTorrent client,
 * so a compromised Blossom server cannot slip in another torrent.
 */
sealed interface TorrentFileCheck {
    /** [url] served bytes that hash to the listing's `x`. */
    data class Verified(
        val url: String,
    ) : TorrentFileCheck

    /** The listing signs no hash (older events): nothing to check [url] against. */
    data class Unchecked(
        val url: String,
    ) : TorrentFileCheck

    /** Every mirror failed or served other bytes. */
    data object Mismatch : TorrentFileCheck

    /** The listing names no `.torrent` mirror. */
    data object NoMirror : TorrentFileCheck

    companion object {
        private const val TAG = "TorrentFileCheck"

        /**
         * Tries [listing]'s mirrors in order with [fetch] (null or a throw means that mirror failed) and
         * returns the first that hashes to its `x`.
         */
        suspend fun check(
            listing: ModelTorrentEvent,
            fetch: suspend (String) -> ByteArray?,
        ): TorrentFileCheck {
            val urls = listing.torrentFileUrls()
            if (urls.isEmpty()) return NoMirror
            val expected = listing.torrentFileSha256()?.lowercase() ?: return Unchecked(urls.first())
            for (url in urls) {
                val bytes =
                    try {
                        fetch(url)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "could not fetch $url", e)
                        null
                    } ?: continue
                if (sha256(bytes).toHexKey() == expected) return Verified(url)
                Log.w(TAG) { "$url does not hash to $expected" }
            }
            return Mismatch
        }

        /**
         * What to hand the BitTorrent client after [check]: the magnet with `xs=` only for a verified
         * mirror, else the plain magnet, whose content the client checks against the signed infohash
         * itself. A user submission has no magnet: its `.torrent` URL, unless the mirror failed.
         */
        fun linkToOpen(
            listing: ModelTorrentEvent,
            check: TorrentFileCheck,
        ): String? =
            when (check) {
                is Verified -> listing.magnetWithExactSource(check.url) ?: check.url
                is Unchecked -> listing.magnetLink() ?: check.url
                Mismatch, NoMirror -> listing.magnetLink()
            }
    }
}
