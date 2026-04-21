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
package com.vitorpamplona.quartz.nip34Git.state.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-34 repository-state ref tag.
 *
 * A branch ref is encoded as:
 *
 *     ["refs/heads/<branch>", "<commit-id>", "<shorthand-parent>", ...]
 *
 * and a tag ref as:
 *
 *     ["refs/tags/<tag>", "<commit-id>", "<shorthand-parent>", ...]
 *
 * The parent elements are optional and allow the maintainer to publish how
 * many commits their branch is ahead of a known ancestor.
 */
class RefTag(
    val name: String,
    val commit: String,
    val parentLineage: List<String>,
) {
    fun toTagArray(): Array<String> = (listOf(name, commit) + parentLineage).toTypedArray()

    val kind: Kind =
        when {
            name.startsWith(PREFIX_HEADS) -> Kind.BRANCH
            name.startsWith(PREFIX_TAGS) -> Kind.TAG
            else -> Kind.OTHER
        }

    val shortName: String =
        when (kind) {
            Kind.BRANCH -> name.removePrefix(PREFIX_HEADS)
            Kind.TAG -> name.removePrefix(PREFIX_TAGS)
            Kind.OTHER -> name
        }

    enum class Kind { BRANCH, TAG, OTHER }

    companion object {
        const val PREFIX_HEADS = "refs/heads/"
        const val PREFIX_TAGS = "refs/tags/"

        fun isRefTag(tag: Array<String>): Boolean {
            if (tag.isEmpty()) return false
            return tag[0].startsWith(PREFIX_HEADS) || tag[0].startsWith(PREFIX_TAGS)
        }

        fun parse(tag: Array<String>): RefTag? {
            ensure(tag.has(1)) { return null }
            ensure(isRefTag(tag)) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val lineage =
                if (tag.size > 2) {
                    tag.drop(2).filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
            return RefTag(tag[0], tag[1], lineage)
        }

        fun branch(
            name: String,
            commit: String,
            parentLineage: List<String> = emptyList(),
        ) = RefTag(PREFIX_HEADS + name, commit, parentLineage)

        fun tag(
            name: String,
            commit: String,
            parentLineage: List<String> = emptyList(),
        ) = RefTag(PREFIX_TAGS + name, commit, parentLineage)
    }
}
