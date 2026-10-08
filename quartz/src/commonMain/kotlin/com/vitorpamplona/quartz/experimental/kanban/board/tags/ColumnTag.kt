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
package com.vitorpamplona.quartz.experimental.kanban.board.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * A Kanban board column, `["col", "<column id>", "<name>", "<order>"]` (NIP PR #1665). The id and
 * the name are required: a column nobody can read cannot be shown. The order is a number the
 * columns are sorted by; a missing or non-numeric order reads as null.
 *
 * The planner app that shares kind 30301 writes `["col", "day"]` — an id with no name — which is
 * why a nameless `col` does not make an event a board.
 */
@Immutable
data class ColumnTag(
    val id: String,
    val name: String,
    val order: Int? = null,
) {
    fun toTagArray() = assemble(id, name, order)

    companion object {
        const val TAG_NAME = "col"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[2].isNotEmpty()

        fun parse(tag: Array<String>): ColumnTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            return ColumnTag(tag[1], tag[2], tag.getOrNull(3)?.toIntOrNull())
        }

        fun assemble(
            id: String,
            name: String,
            order: Int? = null,
        ) = arrayOfNotNull(TAG_NAME, id, name, order?.toString())
    }
}
