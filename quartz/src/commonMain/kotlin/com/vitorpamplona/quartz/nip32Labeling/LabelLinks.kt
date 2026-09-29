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
package com.vitorpamplona.quartz.nip32Labeling

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.each
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelNamespaceTag
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag

/**
 * The NIP-32 `L` namespaces ([LabelNamespaceTag]) and `l` labels ([LabelTag]) an event carries,
 * each a `TAG` by its value. A 1985 labels its targets with them; a 1984 report classifies itself.
 */
internal fun LinkBuilder.labelTags(tags: TagArray) {
    each(tags, LabelNamespaceTag::parse) { tag(Relation.TAG, LabelNamespaceTag.TAG_NAME, it.namespace) }
    each(tags, LabelTag::parse) { tag(Relation.TAG, LabelTag.TAG_NAME, it.label) }
}
