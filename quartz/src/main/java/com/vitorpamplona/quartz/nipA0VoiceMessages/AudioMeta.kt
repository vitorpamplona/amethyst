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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.WaveformTag

data class AudioMeta(
    val url: String,
    val mimeType: String? = null,
    val hash: String? = null,
    val duration: Int? = null,
    val waveform: List<Int>? = null,
) {
    fun toIMetaArray(): Array<String> =
        IMetaTagBuilder(url)
            .apply {
                mimeType?.let { mimeType(it) }
                hash?.let { hash(it) }
                duration?.let { duration(it) }
                waveform?.let { waveform(it) }
            }.build()
            .toTagArray()

    companion object {
        fun parse(iMeta: IMetaTag): AudioMeta =
            AudioMeta(
                url = iMeta.url,
                mimeType = iMeta.mimeType()?.firstOrNull(),
                hash = iMeta.hash()?.firstOrNull(),
                duration = iMeta.duration()?.firstOrNull()?.toIntOrNull(),
                waveform = iMeta.waveform()?.firstOrNull()?.let { WaveformTag.parseWave(it) },
            )
    }
}
