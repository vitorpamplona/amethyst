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
package com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.CoordinateShape
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * What a `z` tag points at. The spec allows three shapes in the same slot, told apart by form:
 *
 * - [EventId]: a 64-char hex id, for a kind 9998 header (or a 9999 used as a nonstandard header);
 * - [Coordinate]: `kind:pubkey:d`, for a kind 39998 header, whose id changes on every edit;
 * - [Name]: anything else — the singular name of a list that was never formally declared,
 *   e.g. `["z", "dog"]`. The spec allows it but prefers a declared header.
 */
@Immutable
sealed interface ParentList {
    /** The raw value to write back in the `z` tag. */
    val value: String

    @Immutable
    data class EventId(
        val eventId: HexKey,
    ) : ParentList {
        override val value get() = eventId
    }

    @Immutable
    data class Coordinate(
        val address: Address,
    ) : ParentList {
        override val value get() = address.toValue()
    }

    @Immutable
    data class Name(
        val name: String,
    ) : ParentList {
        override val value get() = name
    }
}

/** `["z", <header event id | header coordinate | list name>]`: the list an item belongs to. */
class ParentListTag {
    companion object {
        const val TAG_NAME = "z"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun isTagged(
            tag: Array<String>,
            pointer: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == pointer

        fun isTagged(
            tag: Array<String>,
            pointers: Set<String>,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] in pointers

        /** The raw pointer, without classifying it. */
        fun parseValue(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun parse(tag: Array<String>): ParentList? = parseValue(tag)?.let(::classify)

        fun parseEventId(tag: Array<String>): HexKey? {
            val value = parseValue(tag) ?: return null
            return if (isEventId(value)) value else null
        }

        fun parseAddress(tag: Array<String>): Address? {
            val value = parseValue(tag) ?: return null
            return if (looksLikeAddress(value)) AddressSerializer.parse(value) else null
        }

        /** The raw `kind:pubkey:d` value when [tag] points at a coordinate. No parsing, no allocation. */
        fun parseCoordinate(tag: Array<String>): String? {
            val value = parseValue(tag) ?: return null
            return if (looksLikeAddress(value)) value else null
        }

        fun classify(value: String): ParentList {
            if (isEventId(value)) return ParentList.EventId(value)
            if (looksLikeAddress(value)) {
                AddressSerializer.parse(value)?.let { return ParentList.Coordinate(it) }
            }
            return ParentList.Name(value)
        }

        private fun isEventId(value: String) = value.length == 64 && Hex.isHex64(value)

        // Only coordinate-shaped values reach the address parser: it logs a warning for
        // everything it rejects, and a plain list name such as "dog" is not an error.
        private fun looksLikeAddress(value: String) = CoordinateShape.matches(value)

        fun assemble(pointer: String) = arrayOf(TAG_NAME, pointer)

        fun assemble(parent: ParentList) = assemble(parent.value)

        fun assemble(parent: DecentralizedListEvent) = assemble(parent.listPointer())

        fun assemble(parents: List<ParentList>) = parents.map { assemble(it) }
    }
}
