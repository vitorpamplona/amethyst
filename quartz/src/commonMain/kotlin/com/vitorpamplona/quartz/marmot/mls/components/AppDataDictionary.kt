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
import com.vitorpamplona.quartz.marmot.mls.tree.Extension

/**
 * The `app_data_dictionary` MLS extension (draft-ietf-mls-extensions-10 §4.6),
 * extension type `0x0006`.
 *
 * ```text
 * struct {
 *   ComponentData component_data<V>;
 * } AppDataDictionary;
 * ```
 *
 * This is the current Marmot profile's carrier for every piece of
 * application-owned MLS state. It can hang off four different objects, and the
 * container decides the scope:
 *
 * - **GroupContext** — authenticated group state agreed for an epoch. Changes
 *   only through an `AppDataUpdate` proposal.
 * - **LeafNode** — data about one member leaf. Changes only by replacing the
 *   leaf, which is why the account identity proof lives here and is immune to
 *   `AppDataUpdate`.
 * - **KeyPackage** — data about one KeyPackage, separate from the dictionary in
 *   its embedded LeafNode. This is where the empty-data
 *   `last_resort_key_package` component sits.
 * - **GroupInfo** — data for one GroupInfo object.
 *
 * It replaced MIP-01's single `marmot_group_data` extension (`0xF2EE`), which
 * packed every field into one blob that only a whole-extension rewrite could
 * change.
 *
 * ## Ordering is normative
 *
 * Entries are sorted by component id with at most one entry per id, and the
 * draft requires that to be checked on deserialization as well as on
 * construction. This class does both, but differently on purpose: building one
 * from a collection sorts for you, while [decodeTls] rejects bytes that arrive
 * out of order or with a duplicate rather than quietly normalizing them. A
 * receiver that silently sorted would accept two distinct encodings of the same
 * dictionary, and anything hashing or comparing the encoded bytes — a
 * GroupContext, a signature, a conformance snapshot — would then disagree with
 * a peer that rejected one of them.
 */
class AppDataDictionary(
    entries: Collection<ComponentData>,
) : TlsSerializable {
    /** Entries in ascending component-id order, at most one per id. */
    val entries: List<ComponentData> = entries.sortedBy { it.componentId }

    init {
        for (i in 1 until this.entries.size) {
            require(this.entries[i - 1].componentId != this.entries[i].componentId) {
                "AppDataDictionary has a duplicate entry for component " +
                    "0x${this.entries[i].componentId.toString(16).padStart(4, '0')}"
            }
        }
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    val componentIds: List<Int> get() = entries.map { it.componentId }

    operator fun get(componentId: Int): ByteArray? = entries.firstOrNull { it.componentId == componentId }?.data

    fun contains(componentId: Int): Boolean = entries.any { it.componentId == componentId }

    /** A copy with [componentId] set to [data], replacing any existing entry. */
    fun with(
        componentId: Int,
        data: ByteArray,
    ): AppDataDictionary =
        AppDataDictionary(
            entries.filterNot { it.componentId == componentId } + ComponentData(componentId, data),
        )

    /** A copy without [componentId]. Removing an absent id is a no-op. */
    fun without(componentId: Int): AppDataDictionary = AppDataDictionary(entries.filterNot { it.componentId == componentId })

    override fun encodeTls(writer: TlsWriter) {
        writer.putVectorVarInt(entries)
    }

    fun toBytes(): ByteArray {
        val writer = TlsWriter()
        encodeTls(writer)
        return writer.toByteArray()
    }

    /** Wrap as the `0x0006` MLS extension, ready for a LeafNode/GroupContext/KeyPackage list. */
    fun toExtension(): Extension = Extension(EXTENSION_TYPE, toBytes())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppDataDictionary) return false
        return entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String =
        entries.joinToString(prefix = "AppDataDictionary[", postfix = "]") {
            "0x${it.componentId.toString(16).padStart(4, '0')}(${it.data.size}B)"
        }

    companion object {
        /** MLS extension type, pinned by `foundation/registries.md`. */
        const val EXTENSION_TYPE = 0x0006

        val EMPTY = AppDataDictionary(emptyList())

        /**
         * Decode the extension body, enforcing the draft's ordering and
         * uniqueness rules on the wire bytes rather than normalizing them.
         */
        fun decodeTls(reader: TlsReader): AppDataDictionary {
            val decoded = reader.readVectorVarInt { ComponentData.decodeTls(it) }
            for (i in 1 until decoded.size) {
                val previous = decoded[i - 1].componentId
                val current = decoded[i].componentId
                require(previous != current) {
                    "AppDataDictionary contains a duplicate entry for component " +
                        "0x${current.toString(16).padStart(4, '0')}"
                }
                require(previous < current) {
                    "AppDataDictionary entries must be sorted by component id; " +
                        "0x${current.toString(16).padStart(4, '0')} follows " +
                        "0x${previous.toString(16).padStart(4, '0')}"
                }
            }
            return AppDataDictionary(decoded)
        }

        fun decode(bytes: ByteArray): AppDataDictionary {
            val reader = TlsReader(bytes)
            val dictionary = decodeTls(reader)
            require(!reader.hasRemaining) { "AppDataDictionary has trailing bytes" }
            return dictionary
        }

        /**
         * The `0x0006` dictionary in an extension list, or null when the object
         * carries none. An object with more than one is malformed — RFC 9420
         * forbids repeating an extension type — so this throws rather than
         * picking one.
         */
        fun fromExtensions(extensions: List<Extension>): AppDataDictionary? {
            val matches = extensions.filter { it.extensionType == EXTENSION_TYPE }
            if (matches.isEmpty()) return null
            require(matches.size == 1) {
                "an MLS object carries ${matches.size} app_data_dictionary extensions; at most one is valid"
            }
            return decode(matches[0].extensionData)
        }

        /** [fromExtensions], treating an absent dictionary as an empty one. */
        fun fromExtensionsOrEmpty(extensions: List<Extension>): AppDataDictionary = fromExtensions(extensions) ?: EMPTY
    }
}
