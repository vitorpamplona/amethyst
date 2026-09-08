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
package com.vitorpamplona.quartz.experimental.library

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Blossom piece index (kind 32176) — a titled record of a file held on Blossom servers,
 * carrying its size and MIME type.
 *
 * **Not defined by any NIP.** The shape is the publishing client's manifest: title, cover image,
 * summary, `size` and `type`.
 *
 * `size` is published as a string and kept as one. It is a byte count in every event seen, but
 * parsing it to a number here would force a guess about units on anything that is not, and the
 * only consumer is a label.
 */
@Immutable
class BlossomPieceIndexEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), summary(), content).joinToString("\n")

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    /** The file's size as published — see the class doc for why this stays a string. */
    fun size() = firstValue(SIZE_TAG)

    /** The file's MIME type. */
    fun type() = firstValue(TYPE_TAG)

    /** The byte count, when the `size` really is one. Null rather than a guess otherwise. */
    fun sizeInBytes(): Long? = size()?.toLongOrNull()

    /**
     * A direct URL for the whole file (`r`), when the publisher offers one.
     *
     * The pieces are the point of the kind, but reassembling them is a download client's job.
     * Until there is one, this is the single address that opens and plays, which is why it is
     * what the card links to.
     */
    fun url() = firstValue(URL_TAG)

    /** Blossom servers that hold the pieces. Publishers may list more than one. */
    fun blossomServers(): List<String> = allValues(BLOSSOM_TAG)

    /** The whole file's hash (`x`), the name the pieces reassemble into. */
    fun hash() = firstValue(HASH_TAG)

    /**
     * The piece list: each `b` tag is one chunk's hash and, when published, its byte count.
     *
     * Order is the order published — it is the reassembly order, so it must not be sorted.
     */
    fun pieces(): List<BlossomPiece> =
        tags.mapNotNull { tag ->
            if (tag.size > 1 && tag[0] == PIECE_TAG && tag[1].isNotEmpty()) {
                BlossomPiece(tag[1], tag.getOrNull(2)?.toLongOrNull())
            } else {
                null
            }
        }

    fun pieceCount(): Int = tags.count { it.size > 1 && it[0] == PIECE_TAG && it[1].isNotEmpty() }

    fun titleOrIdentifier(): String = title()?.takeIf { it.isNotBlank() } ?: dTag()

    private fun allValues(name: String) =
        tags.mapNotNull { tag ->
            if (tag.size > 1 && tag[0] == name && tag[1].isNotEmpty()) tag[1] else null
        }

    private fun firstValue(name: String) =
        tags.firstNotNullOfOrNull { tag ->
            if (tag.size > 1 && tag[0] == name && tag[1].isNotEmpty()) tag[1] else null
        }

    companion object {
        const val KIND = 32176
        const val SIZE_TAG = "size"
        const val TYPE_TAG = "type"
        const val URL_TAG = "r"
        const val BLOSSOM_TAG = "blossom"
        const val HASH_TAG = "x"
        const val PIECE_TAG = "b"

        fun build(
            title: String,
            dTag: String,
            size: String? = null,
            type: String? = null,
            summary: String? = null,
            image: String? = null,
            description: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BlossomPieceIndexEvent>.() -> Unit = {},
        ): EventTemplate<BlossomPieceIndexEvent> =
            eventTemplate(KIND, description, createdAt) {
                dTag(dTag)
                add(TitleTag.assemble(title))
                size?.let { add(arrayOf(SIZE_TAG, it)) }
                type?.let { add(arrayOf(TYPE_TAG, it)) }
                summary?.let { add(SummaryTag.assemble(it)) }
                image?.let { add(ImageTag.assemble(it)) }

                initializer()
            }
    }
}

/** One chunk of the file: its hash, and its byte count when the publisher included one. */
@Immutable
data class BlossomPiece(
    val hash: String,
    val sizeInBytes: Long?,
)
