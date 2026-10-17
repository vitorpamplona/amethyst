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
package com.vitorpamplona.quartz.nip19Bech32.entities

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.toNEmbed
import com.vitorpamplona.quartz.utils.GZip

@Immutable
data class NEmbed(
    val event: Event,
) : Entity {
    companion object {
        /**
         * Ceiling on the inflated event JSON. This runs on ingest for every note citing a
         * `nembed1…`, and whitespace deflates ~1000:1: unbounded, a ~50 KB payload inflated to
         * 50 MB+ (200 MB of allocation) per citation. A real embedded event is a few KB; anything
         * past this is not one we would render inline anyway.
         */
        const val MAX_INFLATED_BYTES = 256 * 1024

        fun parse(bytes: ByteArray): NEmbed? {
            if (bytes.isEmpty()) return null
            val json = GZip.decompress(bytes, MAX_INFLATED_BYTES) ?: return null
            return NEmbed(Event.fromJson(json))
        }

        fun create(event: Event): String = GZip.compress(event.toJson()).toNEmbed()
    }
}
