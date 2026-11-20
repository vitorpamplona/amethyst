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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.llamagarden.torrent.tags.FileClassTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.HfMatchTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.ModelKindTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * What "Add a torrent" holds, as typed, and what it publishes from it. The form hides fields that do
 * not apply, and this drops whatever a hidden field still holds: a match claim with no HuggingFace
 * link, a quant format or model kind on something that is not a quant.
 */
@Immutable
data class TorrentSubmissionForm(
    val torrentUrl: String = "",
    val name: String = "",
    val sizeGb: String = "",
    val torrentTool: String = "",
    val huggingFaceUrl: String = "",
    val lab: String = "",
    val fileClass: String = FileClassTag.NOT_SAID,
    val modelKind: String = ModelKindTag.NOT_SAID,
    val quantType: String = "",
    val hfMatch: String = HfMatchTag.YES,
) {
    val isQuant get() = fileClass == FileClassTag.QUANT

    val hasHuggingFace get() = huggingFaceUrl.isNotBlank()

    /** A name, and a `.torrent` URL a client can fetch (http or https). */
    val isPublishable get() = name.isNotBlank() && isHttpUrl(torrentUrl)

    /**
     * What the model is underneath: asked only for a quant; a base or fine-tune file is that kind
     * itself, so cards read "Base" rather than "Base · N/A".
     */
    fun effectiveModelKind(): String =
        when (fileClass) {
            FileClassTag.QUANT -> modelKind
            FileClassTag.BASE -> ModelKindTag.BASE
            FileClassTag.FINE_TUNE -> ModelKindTag.FINE_TUNE
            else -> ModelKindTag.NOT_SAID
        }

    /** Bytes, from the GB typed. SI (10⁹), as both llama.garden's viewer and Amethyst display sizes. */
    fun approximateSize(): Long? =
        sizeGb
            .trim()
            .toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { (it * BYTES_PER_GB).toLong() }

    fun template(submitter: HexKey) =
        ModelTorrentEvent.buildSubmission(
            submitter = submitter,
            torrentUrl = torrentUrl,
            name = name,
            approximateSize = approximateSize(),
            huggingFaceUrl = huggingFaceUrl.takeIf { hasHuggingFace },
            fileClass = fileClass,
            modelKind = effectiveModelKind(),
            lab = lab,
            quantType = quantType.takeIf { isQuant },
            torrentTool = torrentTool,
            hfMatch = hfMatch.takeIf { hasHuggingFace },
        )

    companion object {
        private const val BYTES_PER_GB = 1_000_000_000.0

        fun isHttpUrl(url: String): Boolean {
            val trimmed = url.trim()
            val rest =
                when {
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
                    trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
                    else -> return false
                }
            return rest.substringBefore('/').contains('.') && rest.none { it.isWhitespace() }
        }

        /** The typed size, keeping digits and at most one decimal point. */
        fun cleanSize(typed: String): String {
            var seenDot = false
            return typed.filter { c ->
                when {
                    c.isDigit() -> true
                    c == '.' && !seenDot -> true.also { seenDot = true }
                    else -> false
                }
            }
        }
    }
}
