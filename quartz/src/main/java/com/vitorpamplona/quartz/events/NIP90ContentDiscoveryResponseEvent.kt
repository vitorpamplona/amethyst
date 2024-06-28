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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NIP90ContentDiscoveryResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var events: List<HexKey>? = null

    fun innerTags(): List<HexKey> {
        if (content.isEmpty()) {
            return listOf()
        }

        events?.let {
            return it
        }

        try {
            events =
                mapper.readValue<Array<Array<String>>>(content).mapNotNull {
                    if (it.size > 1 && it[0] == "e") {
                        it[1]
                    } else {
                        null
                    }
                }
        } catch (e: Throwable) {
            Log.w("NIP90ContentDiscoveryResponseEvent", "Error parsing the JSON ${e.message}")
        }

        return events ?: listOf()
    }

    companion object {
        const val KIND = 6300
        const val ALT = "NIP90 Content Discovery reply"

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AppRecommendationEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("alt", ALT),
                )
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
