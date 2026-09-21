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
package com.vitorpamplona.quartz.nipCCGeocaching.listing.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The category a [TypeModifier] belongs to.
 *
 * NIP-CC allows at most one `n` modifier per category and lets modifiers from different
 * categories compose freely, so the category — not the modifier — is the unit of exclusivity.
 * That is why this is modelled as a map keyed by category rather than a set of strings.
 */
enum class TypeModifierCategory {
    /** How claims on the treasure are interpreted. */
    CLAIM_SEMANTICS,

    /** What the physical treasure *is*. */
    PRIZE_NATURE,
}

/** The `n` type modifiers NIP-CC defines today. New ones are expected; see [TypeModifierTag]. */
enum class TypeModifier(
    val code: String,
    val category: TypeModifierCategory,
) {
    /** Single-claim cache: the earliest verified found log is the exclusive claim. */
    FIRST_TO_FIND("first-to-find", TypeModifierCategory.CLAIM_SEMANTICS),

    /** The cache itself is a physical work of art. */
    ART("art", TypeModifierCategory.PRIZE_NATURE),
    ;

    companion object {
        fun fromCode(code: String?): TypeModifier? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The `n` tag of a geocache listing (kind 37516): a type modifier.
 *
 * Unknown values parse to null rather than making the listing unreadable — forward
 * compatibility is an explicit requirement of the spec, which reserves the right to define new
 * modifiers and new categories. [parseCode] exposes the raw value for clients that want to show
 * a modifier they don't understand.
 */
class TypeModifierTag {
    companion object {
        const val TAG_NAME = "n"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** The parsed [TypeModifier], or null for a modifier this version does not know. */
        fun parse(tag: Array<String>): TypeModifier? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return TypeModifier.fromCode(tag[1])
        }

        /** The raw `n` value, whether or not it is a known modifier. */
        fun parseCode(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(modifier: TypeModifier) = arrayOf(TAG_NAME, modifier.code)

        fun assemble(modifiers: Collection<TypeModifier>) = modifiers.map { assemble(it) }
    }
}
