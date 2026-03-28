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
 * NIP-32: `L` tag — label namespace.
 *
 * Format: `["L", "<namespace>"]`
 *
 * Namespaces SHOULD be unambiguous (ISO standard or reverse domain name notation).
 * The special `ugc` namespace MAY be used when label content is provided by an end user.
 * `L` tags starting with `#` indicate that the label target should be associated
 * with the label's value (attaching standard nostr tags to events, pubkeys, etc.).
 */
@Immutable
data class LabelNamespaceTag(
    val namespace: String,
) {
    fun toTagArray() = assemble(namespace)

    /**
     * Returns true if this namespace is a tag-association namespace (starts with `#`).
     * When `L` = `#t`, an `l` tag like `["l", "bitcoin", "#t"]` means the target
     * should be associated with the hashtag `bitcoin`.
     */
    fun isTagAssociation() = namespace.startsWith("#")

    companion object {
        const val TAG_NAME = "L"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun isTagged(
            tag: Array<String>,
            namespace: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == namespace

        fun parse(tag: Array<String>): LabelNamespaceTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            return LabelNamespaceTag(tag[1])
        }

        fun parseNamespace(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(namespace: String) = arrayOf(TAG_NAME, namespace)
    }
}
