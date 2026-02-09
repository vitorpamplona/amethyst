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
package com.vitorpamplona.quartz.nip35Torrents

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip35Torrents.tags.BtihTag
import com.vitorpamplona.quartz.nip35Torrents.tags.FileTag
import com.vitorpamplona.quartz.nip35Torrents.tags.InfoHashTag
import com.vitorpamplona.quartz.nip35Torrents.tags.TrackerTag
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.UrlEncoder

@Immutable
class TorrentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun btih() = tags.firstNotNullOfOrNull(BtihTag::parse)

    fun x() = tags.firstNotNullOfOrNull(InfoHashTag::parse)

    fun trackers() = tags.mapNotNull(TrackerTag::parse)

    fun files() = tags.mapNotNull(FileTag::parse)

    fun toMagnetLink(): String? {
        val btih = btih()?.ifBlank { null } ?: return null
        val title = title()?.ifBlank { null }

        return buildString {
            append("magnet:?xt=urn:btih:")
            append(btih)
            append("&")
            if (title != null) {
                append("dn=")
                append(UrlEncoder.encode(title))
                append("&")
            }
            trackers().forEachIndexed { idx, trackerUrl ->
                if (idx > 0) {
                    append("&")
                }
                append("tr=")
                append(UrlEncoder.encode(trackerUrl))
            }
        }
    }

    fun totalSizeBytes(): Long = tags.sumOf { FileTag.parseBytes(it) ?: 0L }

    companion object {
        const val KIND = 2003
        const val ALT_DESCRIPTION = "A torrent file"

        fun build(
            description: String?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TorrentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description ?: "", createdAt) {
            alt(ALT_DESCRIPTION)
            initializer()
        }

        fun build(
            title: String,
            btih: String,
            files: List<FileTag>,
            description: String? = null,
            x: String? = null,
            trackers: List<String>? = null,
            alt: String? = null,
            contentWarningReason: String? = null,
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate(KIND, description ?: "", createdAt) {
            alt(alt ?: ALT_DESCRIPTION)
            title(title)
            btih(btih)
            files(files)
            trackers?.let { trackers(it) }
            x?.let { infohash(it) }
            contentWarningReason?.let { contentWarning(it) }

            description?.let {
                hashtags(findHashtags(it))
                references(findURLs(it))
                quotes(findNostrUris(it))
            }
        }
    }
}
