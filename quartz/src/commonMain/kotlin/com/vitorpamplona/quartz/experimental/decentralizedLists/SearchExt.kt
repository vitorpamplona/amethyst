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

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.NamesTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.SingularPlural
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TitlesTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.CommentsTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.NameTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.TitleTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.DescriptionTag
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
    // The read path runs once per event per keystroke, so this is two allocation-free passes
    // instead of one full scan (and one parsed object) per field. The first pass only remembers
    // the first well-formed tag of each field; the fixed visiting order is applied afterwards.
    var names: Array<String>? = null
    var titles: Array<String>? = null
    var name: String? = null
    var title: String? = null
    var description: String? = null
    var comments: String? = null

    fastForEach { tag ->
        if (tag.size < 2 || tag[1].isEmpty()) return@fastForEach
        when (tag[0]) {
            NamesTag.TAG_NAME -> if (names == null && SingularPlural.isTag(tag, NamesTag.TAG_NAME)) names = tag
            TitlesTag.TAG_NAME -> if (titles == null && SingularPlural.isTag(tag, TitlesTag.TAG_NAME)) titles = tag
            NameTag.TAG_NAME -> if (name == null) name = tag[1]
            TitleTag.TAG_NAME -> if (title == null) title = tag[1]
            DescriptionTag.TAG_NAME -> if (description == null) description = tag[1]
            CommentsTag.TAG_NAME -> if (comments == null) comments = tag[1]
        }
    }

    names?.let {
        if (!visitor.visit(it[1])) return false
        if (!visitor.visit(it[2])) return false
    }
    titles?.let {
        if (!visitor.visit(it[1])) return false
        if (!visitor.visit(it[2])) return false
    }
    name?.let { if (!visitor.visit(it)) return false }
    title?.let { if (!visitor.visit(it)) return false }
    description?.let { if (!visitor.visit(it)) return false }
    comments?.let { if (!visitor.visit(it)) return false }
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
