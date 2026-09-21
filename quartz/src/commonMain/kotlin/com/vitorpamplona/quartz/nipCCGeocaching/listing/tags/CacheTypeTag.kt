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
 * The cache types NIP-CC names as common. The vocabulary is deliberately open — the spec says
 * types "are determined by individual clients" — so [CacheTypeTag.parseTypeCode] hands back
 * unknown codes unchanged and only [CacheTypeTag.parse] narrows to this set.
 */
enum class CacheType(
    val code: String,
) {
    TRADITIONAL("traditional"),
    MULTI("multi"),
    MYSTERY("mystery"),
    ;

    companion object {
        fun fromCode(code: String?): CacheType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The `t` tag of a geocache listing (kind 37516).
 *
 * `t` carries two unrelated things on this kind: the cache *type* (`traditional`, `multi`,
 * `mystery`, or anything a client invents) and the retirement marker [ARCHIVED], which owners
 * add to preserve a cache's history instead of deleting it. A third meaning — the log type on
 * a kind 1111 comment — lives in
 * [com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogTypeTag]. The parsers are
 * kept apart per kind on purpose: sharing one would let `archived` read as a cache type.
 */
class CacheTypeTag {
    companion object {
        const val TAG_NAME = "t"

        /** `["t", "archived"]` retires a listing. It is a lifecycle marker, never a cache type. */
        const val ARCHIVED = "archived"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** The raw `t` value, including [ARCHIVED]. */
        fun parseCode(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        /** The raw `t` value when it names a cache type — never [ARCHIVED]. */
        fun parseTypeCode(tag: Array<String>): String? = parseCode(tag)?.takeIf { it != ARCHIVED }

        /** The parsed [CacheType], or null when the value is [ARCHIVED], missing, or client-defined. */
        fun parse(tag: Array<String>): CacheType? = CacheType.fromCode(parseTypeCode(tag))

        fun isArchived(tag: Array<String>) = parseCode(tag) == ARCHIVED

        fun assemble(type: CacheType) = arrayOf(TAG_NAME, type.code)

        fun assemble(code: String) = arrayOf(TAG_NAME, code)

        fun assembleArchived() = arrayOf(TAG_NAME, ARCHIVED)
    }
}
