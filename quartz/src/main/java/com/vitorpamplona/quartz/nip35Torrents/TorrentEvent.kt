/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip35Torrents

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.mapValues
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class TorrentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstTagValue("title")

    fun btih() = tags.firstTagValue("btih")

    fun x() = tags.firstTagValue("x")

    fun trackers() = tags.mapValues("tracker")

    fun files() = tags.filter { it.size > 1 && it[0] == "file" }.map { TorrentFile(it[1], it.getOrNull(2)?.toLongOrNull()) }

    fun toMagnetLink(): String {
        val builder = Uri.Builder()
        builder
            .scheme("magnet")
            .appendQueryParameter("xt", "urn:btih:${btih()}")
            .appendQueryParameter("dn", title())

        trackers().ifEmpty { DEFAULT_TRACKERS }.forEach {
            builder.appendQueryParameter("tr", it)
        }

        return builder.build().toString()
    }

    fun totalSizeBytes(): Long = tags.filter { it.size > 1 && it[0] == "file" }.sumOf { it.getOrNull(2)?.toLongOrNull() ?: 0L }

    companion object {
        const val KIND = 2003
        const val ALT_DESCRIPTION = "A torrent file"

        val DEFAULT_TRACKERS =
            listOf(
                "http://tracker.loadpeers.org:8080/xvRKfvAlnfuf5EfxTT5T0KIVPtbqAHnX/announce",
                "udp://tracker.coppersurfer.tk:6969/announce",
                "udp://tracker.openbittorrent.com:6969/announce",
                "udp://open.stealth.si:80/announce",
                "udp://tracker.torrent.eu.org:451/announce",
                "udp://tracker.opentrackr.org:1337",
            )

        fun create(
            title: String,
            btih: String,
            files: List<TorrentFile>,
            description: String? = null,
            x: String? = null,
            trackers: List<String>? = null,
            alt: String? = null,
            sensitiveContent: Boolean? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (TorrentEvent) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    arrayOf("title", title),
                    arrayOf("btih", btih),
                    x?.let { arrayOf("x", it) },
                    alt?.let { arrayOf("alt", it) } ?: AltTagSerializer.toTagArray(ALT_DESCRIPTION),
                    sensitiveContent?.let {
                        if (it) {
                            ContentWarningSerializer.toTagArray()
                        } else {
                            null
                        }
                    },
                ) +
                    files.map {
                        if (it.bytes != null) {
                            arrayOf(it.fileName, it.bytes.toString())
                        } else {
                            arrayOf(it.fileName)
                        }
                    } +
                    (
                        trackers?.map {
                            arrayOf(it)
                        } ?: emptyList()
                    )

            val content = description ?: ""
            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
