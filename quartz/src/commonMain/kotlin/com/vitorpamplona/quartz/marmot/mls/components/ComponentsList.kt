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
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/**
 * The `ComponentsList` payload shared by the upstream `app_components`
 * (`0x0001`) and `safe_aad` (`0x0002`) components
 * (draft-ietf-mls-extensions-10).
 *
 * ```text
 * struct {
 *   ComponentID component_ids<V>;
 * } ComponentsList;
 * ```
 *
 * On the wire that is a QUIC varint giving the payload's BYTE length —
 * `2 * id count`, not the count — followed by big-endian `uint16` ids.
 *
 * The same shape means two different things depending on where it sits:
 *
 * - in a **LeafNode** dictionary it is the component ids that member supports;
 * - in a **GroupContext** dictionary it is the ids the group requires.
 *
 * A member that does not support every required id cannot join. Note the
 * asymmetry Marmot relies on: a GroupContext may require a component whose data
 * is leaf-only (`0x8009`, the account identity proof), so "required" does not
 * imply "present in the GroupContext dictionary".
 *
 * Anyone advertising `app_data_dictionary` support must also understand
 * `app_components` and `safe_aad`, which is why a client with nothing to
 * contribute to SafeAAD still carries an explicit empty list rather than
 * omitting the component.
 */
object ComponentsList {
    /**
     * Encode [ids] as a component payload. Sorted and de-duplicated on the way
     * out, because ordering is part of the value: two encodings of the same set
     * would otherwise hash differently inside a signed GroupContext.
     */
    fun encode(ids: Collection<Int>): ByteArray {
        val sorted = ids.distinct().sorted()
        for (id in sorted) {
            require(id in 0..0xFFFF) { "ComponentID must fit in a uint16, was $id" }
        }
        val inner = TlsWriter()
        for (id in sorted) inner.putUint16(id)

        val writer = TlsWriter()
        writer.putOpaqueVarInt(inner.toByteArray())
        return writer.toByteArray()
    }

    /**
     * Decode a component payload, rejecting an odd byte length, duplicates,
     * out-of-order ids, and trailing bytes.
     *
     * Strict for the same reason [AppDataDictionary.decodeTls] is: this list
     * lives inside signed group state, so accepting a second spelling of the
     * same list would let two peers hold bytes they each consider valid and
     * disagree about.
     */
    fun decode(bytes: ByteArray): List<Int> {
        val reader = TlsReader(bytes)
        val payload = reader.readOpaqueVarInt()
        require(!reader.hasRemaining) { "ComponentsList has trailing bytes" }
        require(payload.size % 2 == 0) {
            "ComponentsList payload must be a whole number of uint16 ids, was ${payload.size} bytes"
        }

        val payloadReader = TlsReader(payload)
        val ids = mutableListOf<Int>()
        while (payloadReader.hasRemaining) {
            val id = payloadReader.readUint16()
            val previous = ids.lastOrNull()
            if (previous != null) {
                require(previous != id) {
                    "ComponentsList contains a duplicate id 0x${id.toString(16).padStart(4, '0')}"
                }
                require(previous < id) {
                    "ComponentsList must be sorted; 0x${id.toString(16).padStart(4, '0')} follows " +
                        "0x${previous.toString(16).padStart(4, '0')}"
                }
            }
            ids.add(id)
        }
        return ids
    }

    /** Component id of the upstream `app_components` list. */
    const val APP_COMPONENTS_ID = 0x0001

    /** Component id of the upstream `safe_aad` list. */
    const val SAFE_AAD_ID = 0x0002

    /** The supported/required id list carried by [dictionary], or empty when absent. */
    fun supportedOrRequired(dictionary: AppDataDictionary): List<Int> = dictionary[APP_COMPONENTS_ID]?.let { decode(it) } ?: emptyList()
}
