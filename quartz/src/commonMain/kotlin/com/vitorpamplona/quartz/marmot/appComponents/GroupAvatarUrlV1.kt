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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/**
 * `marmot.group.avatar-url.v1`, component `0x8007` — a group avatar behind an
 * ordinary `https` URL.
 *
 * ```text
 * struct {
 *   opaque url<0..2048>;
 *   opaque dim<0..256>;
 *   opaque thumbhash<0..256>;
 * } MarmotGroupAvatarUrlV1;
 * ```
 *
 * The lightweight alternative to [GroupBlossomImageV1]: no key material, no
 * encrypted blob, just a link. An absent avatar is the empty state — an empty
 * `url` AND empty hints; a partially-empty state is invalid, so "no avatar" has
 * exactly one encoding.
 *
 * [dim] and [thumbhash] are opaque by contract. A decoder checks only their
 * length, and a hint it cannot interpret is treated as absent rather than
 * invalidating otherwise-valid group state — which is why they are kept as raw
 * bytes here and interpreted only at render time ([dimensions]).
 *
 * **Precedence:** a group may carry this AND [GroupBlossomImageV1]. When both
 * are present the URL avatar wins; clearing this one falls back to the Blossom
 * image.
 */
data class GroupAvatarUrlV1(
    val url: String,
    val dim: ByteArray = ByteArray(0),
    val thumbhash: ByteArray = ByteArray(0),
) {
    /** True for the cleared/absent avatar. */
    val isAbsent: Boolean get() = url.isEmpty()

    /**
     * `dim` read as the conventional `WIDTHxHEIGHT`, or null when it is absent
     * or in a shape this renderer does not understand. Never an error: an
     * uninterpretable hint is not a validity problem.
     */
    val dimensions: Pair<Int, Int>?
        get() {
            if (dim.isEmpty()) return null
            val text =
                try {
                    dim.decodeToString(throwOnInvalidSequence = true)
                } catch (_: Exception) {
                    return null
                }
            val parts = text.split('x', 'X')
            if (parts.size != 2) return null
            val w = parts[0].toIntOrNull() ?: return null
            val h = parts[1].toIntOrNull() ?: return null
            if (w <= 0 || h <= 0) return null
            return w to h
        }

    fun encode(): ByteArray {
        require(!(isAbsent && (dim.isNotEmpty() || thumbhash.isNotEmpty()))) {
            "group avatar absent state must not carry hints"
        }
        require(dim.size <= HINT_MAX_BYTES) { "group avatar dim exceeds $HINT_MAX_BYTES bytes" }
        require(thumbhash.size <= HINT_MAX_BYTES) { "group avatar thumbhash exceeds $HINT_MAX_BYTES bytes" }

        // Normalizing at encode is the producer's job: the stored bytes ARE the
        // serialized form, and every decoder re-derives them to check.
        val stored = if (isAbsent) "" else MarmotHttpsUrl.normalize(url)

        val writer = TlsWriter()
        writer.putOpaqueVarInt(stored.encodeToByteArray())
        writer.putOpaqueVarInt(dim)
        writer.putOpaqueVarInt(thumbhash)
        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupAvatarUrlV1) return false
        return url == other.url && dim.contentEquals(other.dim) && thumbhash.contentEquals(other.thumbhash)
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + dim.contentHashCode()
        result = 31 * result + thumbhash.contentHashCode()
        return result
    }

    companion object {
        const val COMPONENT_ID = AppComponentIds.GROUP_AVATAR_URL_V1
        const val URL_MAX_BYTES = MarmotHttpsUrl.MAX_BYTES
        const val HINT_MAX_BYTES = 256

        /** The cleared avatar: every field empty. */
        val ABSENT = GroupAvatarUrlV1("")

        fun decode(bytes: ByteArray): GroupAvatarUrlV1 {
            val reader = TlsReader(bytes)
            val url = reader.readOpaqueVarInt()
            val dim = reader.readOpaqueVarInt()
            val thumbhash = reader.readOpaqueVarInt()
            require(!reader.hasRemaining) { "group avatar component has trailing bytes" }
            require(url.size <= URL_MAX_BYTES) { "group avatar URL exceeds $URL_MAX_BYTES bytes" }
            require(dim.size <= HINT_MAX_BYTES) { "group avatar dim exceeds $HINT_MAX_BYTES bytes" }
            require(thumbhash.size <= HINT_MAX_BYTES) { "group avatar thumbhash exceeds $HINT_MAX_BYTES bytes" }

            val text = url.decodeToString()
            // Presence is decided on the bytes, before anything is parsed.
            require(!(text.isEmpty() && (dim.isNotEmpty() || thumbhash.isNotEmpty()))) {
                "group avatar absent state must not carry hints"
            }
            if (text.isNotEmpty()) {
                // "A decoder re-runs validation and the WHATWG parse-and-serialize
                // on the decoded url and MUST reject state whose stored URL bytes
                // differ from the serializer's output." A decoder never repairs a
                // non-normalized URL into canonical state — two members would then
                // hold different bytes for the same group.
                require(MarmotHttpsUrl.normalize(text) == text) { "group avatar URL is not normalized" }
            }
            return GroupAvatarUrlV1(text, dim, thumbhash)
        }
    }
}
