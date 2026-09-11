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
package com.vitorpamplona.quartz.marmot.mls.components

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/**
 * One entry in an [AppDataDictionary] (draft-ietf-mls-extensions-10 §4.6).
 *
 * ```text
 * uint16 ComponentID;
 *
 * struct {
 *   ComponentID component_id;
 *   opaque data<V>;
 * } ComponentData;
 * ```
 *
 * MLS owns this framing; the application owns whatever is inside [data]. For
 * Marmot's own ids the payload uses the Marmot binary profile — the same QUIC
 * variable-length prefixes MLS uses — but that is a coincidence of taste, not a
 * rule this struct enforces. Nothing here interprets [data].
 */
data class ComponentData(
    val componentId: Int,
    val data: ByteArray,
) : TlsSerializable {
    init {
        require(componentId in 0..0xFFFF) {
            "ComponentID must fit in a uint16, was $componentId"
        }
    }

    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(componentId)
        writer.putOpaqueVarInt(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComponentData) return false
        return componentId == other.componentId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * componentId + data.contentHashCode()

    companion object {
        fun decodeTls(reader: TlsReader): ComponentData =
            ComponentData(
                componentId = reader.readUint16(),
                data = reader.readOpaqueVarInt(),
            )
    }
}
