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
 * `marmot.group.profile.v1`, component `0x8001` — the group's display name and
 * description.
 *
 * ```text
 * struct {
 *   opaque name<0..256>;
 *   opaque description<0..4096>;
 * } MarmotGroupProfileV1;
 * ```
 *
 * Protocol equality is BYTE equality. Clients MUST NOT Unicode-normalize before
 * hashing, signing, comparing, or storing this state — two visually identical
 * names that differ in normalization form are different group states, and
 * normalizing on the way in would make us disagree with a peer that did not.
 *
 * An empty name is valid at the protocol layer; rendering a fallback is an
 * application choice. Note the distinction the component draws between an
 * absent component and a present one holding two empty fields: the latter is a
 * signed empty profile, and they are different canonical states even if an
 * application renders them the same.
 */
data class GroupProfileV1(
    val name: String,
    val description: String,
) {
    init {
        require(name.encodeToByteArray().size <= NAME_MAX_BYTES) {
            "group profile name exceeds $NAME_MAX_BYTES bytes"
        }
        require(description.encodeToByteArray().size <= DESCRIPTION_MAX_BYTES) {
            "group profile description exceeds $DESCRIPTION_MAX_BYTES bytes"
        }
    }

    fun encode(): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(name.encodeToByteArray())
        writer.putOpaqueVarInt(description.encodeToByteArray())
        return writer.toByteArray()
    }

    companion object {
        const val COMPONENT_ID = AppComponentIds.GROUP_PROFILE_V1
        const val NAME_MAX_BYTES = 256
        const val DESCRIPTION_MAX_BYTES = 4096

        fun decode(bytes: ByteArray): GroupProfileV1 {
            val reader = TlsReader(bytes)
            val name = reader.readOpaqueVarInt()
            val description = reader.readOpaqueVarInt()
            require(!reader.hasRemaining) { "group profile component has trailing bytes" }
            require(name.size <= NAME_MAX_BYTES) { "group profile name exceeds $NAME_MAX_BYTES bytes" }
            require(description.size <= DESCRIPTION_MAX_BYTES) {
                "group profile description exceeds $DESCRIPTION_MAX_BYTES bytes"
            }
            return GroupProfileV1(name.decodeToString(), description.decodeToString())
        }
    }
}
