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
package com.vitorpamplona.quartz.nip01Core.diff

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Kind

/** An entry that exists in both versions, but with different details. */
@Immutable
data class DiffChange(
    val before: DiffEntry,
    val after: DiffEntry,
)

/**
 * What happened to the part of the content that is not already represented by [DiffEntry]s.
 * For NIP-51 lists that is the NIP-44 encrypted private items, which can only be compared
 * as a whole without decrypting.
 */
enum class ContentChange {
    NONE,
    ADDED,
    CHANGED,
    CLEARED,
    ;

    companion object {
        fun between(
            older: String,
            newer: String,
        ): ContentChange =
            when {
                older == newer -> NONE
                older.isBlank() -> ADDED
                newer.isBlank() -> CLEARED
                else -> CHANGED
            }
    }
}

/**
 * Everything that differs between two versions of the same event, computed by
 * [com.vitorpamplona.quartz.nip01Core.core.Event.diffFrom].
 *
 * @property contentEncrypted whether [content] refers to encrypted content (private list
 * items) rather than plain text.
 */
@Immutable
class EventDiff(
    val kind: Kind,
    val removed: List<DiffEntry>,
    val added: List<DiffEntry>,
    val changed: List<DiffChange>,
    val content: ContentChange,
    val contentEncrypted: Boolean,
) {
    fun isEmpty() = removed.isEmpty() && added.isEmpty() && changed.isEmpty() && content == ContentChange.NONE

    /** The newer version dropped something the older one had. */
    fun removesData() = removed.isNotEmpty() || content == ContentChange.CLEARED

    companion object {
        fun compute(
            kind: Kind,
            older: List<DiffEntry>,
            newer: List<DiffEntry>,
            content: ContentChange,
            contentEncrypted: Boolean,
        ): EventDiff {
            val olderByKey = LinkedHashMap<String, DiffEntry>(older.size)
            older.forEach { olderByKey.getOrPut(it.key) { it } }
            val newerByKey = LinkedHashMap<String, DiffEntry>(newer.size)
            newer.forEach { newerByKey.getOrPut(it.key) { it } }

            val removed = mutableListOf<DiffEntry>()
            val changed = mutableListOf<DiffChange>()
            olderByKey.forEach { (key, before) ->
                val after = newerByKey[key]
                when {
                    after == null -> removed.add(before)
                    after != before -> changed.add(DiffChange(before, after))
                }
            }
            val added = newerByKey.filterKeys { it !in olderByKey }.values.toList()

            return EventDiff(kind, removed, added, changed, content, contentEncrypted)
        }
    }
}
