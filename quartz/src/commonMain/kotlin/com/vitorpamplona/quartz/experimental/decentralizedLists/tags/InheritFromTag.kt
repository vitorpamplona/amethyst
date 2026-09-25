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
package com.vitorpamplona.quartz.experimental.decentralizedLists.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * What a `b` tag claims about its target. A closed registry: anything else, including an
 * absent third element, reads as [POINTER] — the least-commitment reading, so that an
 * underspecified tag never grants live deference. Code that acts on deference must gate on
 * [INHERIT] / [INHERIT_ITEMS] explicitly, never on "not pointer".
 */
enum class InheritType(
    val code: String,
) {
    /** "My object corresponds to that one." No deference, no resolution semantics. */
    POINTER("pointer"),

    /** "My definition is this parent's, unless I state otherwise." Live, followed on read. */
    INHERIT("inherit"),

    /** "My list's items are this parent's items, plus my own." Additive; no definition fields. */
    INHERIT_ITEMS("inherit-items"),
    ;

    companion object {
        fun fromCode(code: String?) =
            when (code) {
                INHERIT.code -> INHERIT
                INHERIT_ITEMS.code -> INHERIT_ITEMS
                else -> POINTER
            }
    }
}

/**
 * Tapestry Inherit-From: `["b", <target kind:pubkey:d>, <type>]`, on kinds 39998 and 39999.
 *
 * Child-claims-parent, and unlike the `n`/`s` class-thread tags it is **not** flipped: the
 * derived relationship points child → target for every type.
 *
 * Order matters only among inherit-typed tags (the first-listed parent wins a field conflict);
 * pointer and inherit-items tags carry no meaning in their position.
 */
@Immutable
data class InheritFromTag(
    val target: String,
    val type: InheritType = InheritType.POINTER,
) {
    fun targetAddress() = Address.parse(target)

    fun toTagArray() = assemble(target, type)

    companion object {
        const val TAG_NAME = "b"

        /**
         * The one reserved non-address value: `["b", "b-tag-deferred"]` marks its carrier as
         * *deliberately* unaffiliated ("I considered a shared twin and chose none"). It derives
         * nothing — no edge, no target — so [parse] never returns it as a target.
         */
        const val UNAFFILIATED = "b-tag-deferred"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[1] != UNAFFILIATED

        fun isUnaffiliatedMarker(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == UNAFFILIATED

        fun parse(tag: Array<String>): InheritFromTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[1] != UNAFFILIATED) { return null }
            return InheritFromTag(tag[1], InheritType.fromCode(tag.getOrNull(2)))
        }

        /** The target when [tag] is a `b` of exactly [type]. */
        fun parseTarget(
            tag: Array<String>,
            type: InheritType,
        ): String? = parse(tag)?.takeIf { it.type == type }?.target

        // The type is always written out, pointer included: readers treat a missing one as
        // pointer anyway, and an explicit value says the author meant it.
        fun assemble(
            target: String,
            type: InheritType,
        ) = arrayOf(TAG_NAME, target, type.code)

        fun assemble(
            target: Address,
            type: InheritType,
        ) = assemble(target.toValue(), type)

        fun assembleUnaffiliated() = arrayOf(TAG_NAME, UNAFFILIATED)
    }
}
