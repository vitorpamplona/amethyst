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
package com.vitorpamplona.quartz.podcasts

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Podcasting-2.0 keysend metadata blob ("boostagram") carried in TLV record
 * [PodcastValue.PODCAST_TLV_RECORD] (7629169). It tells the receiving node which podcast/episode the
 * payment is for and how much was sent in total. Field names follow the satoshis.stream convention
 * (<https://github.com/satoshisstream/satoshis.stream/blob/main/TLV_registry.md>).
 *
 * Unset fields are omitted from the JSON ([JsonMapper] does not encode defaults), keeping the record
 * small enough to fit comfortably inside a keysend onion.
 */
@Serializable
class PodcastBoostagram(
    val podcast: String? = null,
    val episode: String? = null,
    /** "stream" for per-minute streaming sats, "boost" for a deliberate lump-sum tip. */
    val action: String? = null,
    @SerialName("app_name")
    val appName: String? = null,
    /** Total sats (not millisats) the listener sent across all splits. */
    @SerialName("value_msat_total")
    val valueMsatTotal: Long? = null,
    val message: String? = null,
    @SerialName("sender_name")
    val senderName: String? = null,
) {
    fun toJson(): String = JsonMapper.toJson(this)

    companion object {
        const val ACTION_STREAM = "stream"
        const val ACTION_BOOST = "boost"
    }
}
