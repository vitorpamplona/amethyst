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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.names
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.titles
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.comments
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.name
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.title
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor

/**
 * The human-authored text of any kind in the family, in a fixed order: the header's `names`
 * and `titles` (singular, then plural), the item's `name` and `title`, `description`,
 * `comments`, then each `t` item value. Headers and items share one walk because the spec's
 * nonstandard method lets an item carry header tags; each kind simply has fewer of them set.
 *
 * `content` is not part of the spec and ids/pubkeys/coordinates are served by tag filters,
 * so neither is indexed.
 *
 * @return false when the visitor stopped the walk.
 */
fun TagArray.forEachSearchableListField(visitor: IndexableFieldVisitor): Boolean {
    names()?.let {
        if (!visitor.visit(it.singular)) return false
        if (!visitor.visit(it.plural)) return false
    }
    titles()?.let {
        if (!visitor.visit(it.singular)) return false
        if (!visitor.visit(it.plural)) return false
    }
    name()?.let { if (!visitor.visit(it)) return false }
    title()?.let { if (!visitor.visit(it)) return false }
    description()?.let { if (!visitor.visit(it)) return false }
    comments()?.let { if (!visitor.visit(it)) return false }
    fastForEach { tag ->
        HashtagTag.parse(tag)?.let { if (!visitor.visit(it)) return false }
    }
    return true
}

/** The write-path join of [forEachSearchableListField]: one field per line. */
fun TagArray.searchableListContent() =
    buildString {
        forEachSearchableListField { field ->
            if (field != null) {
                if (isNotEmpty()) append('\n')
                append(field)
            }
            true
        }
    }
