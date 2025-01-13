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
package com.vitorpamplona.quartz.nip71Video

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.addressables.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class VideoViewEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 34237

        fun create(
            video: ATag,
            signer: NostrSigner,
            viewStart: Long?,
            viewEnd: Long?,
            createdAt: Long = TimeUtils.now(),
            onReady: (VideoViewEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()

            val aTag = video.toTag()
            tags.add(arrayOf("d", aTag))
            tags.add(arrayOf("a", aTag))
            if (viewEnd != null) {
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0", viewEnd.toString()))
            } else {
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0"))
            }

            signer.sign(createdAt, KIND, tags.toTypedArray(), "", onReady)
        }

        fun addViewedTime(
            event: VideoViewEvent,
            viewStart: Long?,
            viewEnd: Long?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (VideoViewEvent) -> Unit,
        ) {
            val tags = event.tags.toMutableList()
            if (viewEnd != null) {
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0", viewEnd.toString()))
            } else {
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0"))
            }

            signer.sign(createdAt, KIND, tags.toTypedArray(), "", onReady)
        }
    }
}