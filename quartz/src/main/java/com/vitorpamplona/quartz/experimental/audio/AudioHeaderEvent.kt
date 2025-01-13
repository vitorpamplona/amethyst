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
package com.vitorpamplona.quartz.experimental.audio

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AudioHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun download() = tags.firstOrNull { it.size > 1 && it[0] == DOWNLOAD_URL }?.get(1)

    fun stream() = tags.firstOrNull { it.size > 1 && it[0] == STREAM_URL }?.get(1)

    fun wavefrom() =
        tags
            .firstOrNull { it.size > 1 && it[0] == WAVEFORM }
            ?.get(1)
            ?.let { EventMapper.mapper.readValue<List<Int>>(it) }

    companion object {
        const val KIND = 1808
        const val ALT = "Audio header"

        private const val DOWNLOAD_URL = "download_url"
        private const val STREAM_URL = "stream_url"
        private const val WAVEFORM = "waveform"

        fun create(
            description: String,
            downloadUrl: String,
            streamUrl: String? = null,
            wavefront: String? = null,
            sensitiveContent: Boolean? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AudioHeaderEvent) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    downloadUrl.let { arrayOf(DOWNLOAD_URL, it) },
                    streamUrl?.let { arrayOf(STREAM_URL, it) },
                    wavefront?.let { arrayOf(WAVEFORM, it) },
                    sensitiveContent?.let {
                        if (it) {
                            arrayOf("content-warning", "")
                        } else {
                            null
                        }
                    },
                    arrayOf("alt", ALT),
                ).toTypedArray()

            signer.sign(createdAt, KIND, tags, description, onReady)
        }
    }
}
