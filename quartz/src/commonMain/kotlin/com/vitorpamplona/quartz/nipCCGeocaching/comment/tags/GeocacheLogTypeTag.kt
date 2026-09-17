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
package com.vitorpamplona.quartz.nipCCGeocaching.comment.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** The log types NIP-CC defines for a kind 1111 comment on a geocache. */
enum class GeocacheLogType(
    val code: String,
) {
    /** Did not find — the searcher looked and came away empty-handed. */
    DNF("dnf"),

    /** Helpful or neutral context. The default when a comment does not say. */
    NOTE("note"),

    /** The cache needs attention: wet, full, damaged, muggled. */
    MAINTENANCE("maintenance"),

    /** The owner retiring the cache, preserving its history rather than deleting it. */
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromCode(code: String?): GeocacheLogType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The `t` tag of a geocache log comment (kind 1111).
 *
 * `t` is also NIP-01's hashtag tag, and a log comment may carry both. Parsing to the enum rather
 * than to the raw value is what keeps them apart: a hashtag is simply not one of these four codes
 * and falls through.
 *
 * Not to be confused with the `t` on a *listing*, which carries the cache type and the archived
 * marker — see [com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheTypeTag].
 */
class GeocacheLogTypeTag {
    companion object {
        const val TAG_NAME = "t"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): GeocacheLogType? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return GeocacheLogType.fromCode(tag[1])
        }

        fun assemble(type: GeocacheLogType) = arrayOf(TAG_NAME, type.code)
    }
}
