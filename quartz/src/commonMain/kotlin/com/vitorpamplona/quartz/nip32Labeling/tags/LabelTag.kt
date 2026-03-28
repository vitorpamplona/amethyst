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
package com.vitorpamplona.quartz.nip32Labeling.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-32: `l` tag — label value with an optional namespace mark.
 *
 * Format: `["l", "<label>", "<namespace>"]`
 *
 * If no namespace mark is included, `ugc` is implied.
 */
@Immutable
data class LabelTag(
    val label: String,
    val namespace: String,
) {
    fun toTagArray() = assemble(label, namespace)

    companion object {
        const val TAG_NAME = "l"
        const val DEFAULT_NAMESPACE = "ugc"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun isTagged(
            tag: Array<String>,
            label: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == label

        fun isTaggedWithNamespace(
            tag: Array<String>,
            namespace: String,
        ) = tag.has(2) && tag[0] == TAG_NAME && tag[2] == namespace

        fun parse(tag: Array<String>): LabelTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            val namespace = if (tag.has(2) && tag[2].isNotEmpty()) tag[2] else DEFAULT_NAMESPACE
            return LabelTag(tag[1], namespace)
        }

        fun parseLabel(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(
            label: String,
            namespace: String,
        ) = arrayOf(TAG_NAME, label, namespace)
    }
}
