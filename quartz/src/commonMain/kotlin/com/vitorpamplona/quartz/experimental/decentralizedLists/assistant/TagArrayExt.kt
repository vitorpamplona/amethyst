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
package com.vitorpamplona.quartz.experimental.decentralizedLists.assistant

import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.tags.AssistantDesignation
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.tags.AssistantDesignationTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.tags.DListCuration
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.tags.DListCurationTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray

// Both entry families ride NIP-85's kind 10040 but are bounded away from it: NIP-85 only reads
// 30382-30385 keys, and these parsers only read 39998/39999 ones.

/** The blanket assistant designation. On duplicates the first occurrence wins. */
fun TagArray.dListAssistant() = firstNotNullOfOrNull(AssistantDesignationTag::parse)

/** Every per-list curation entry, first occurrence per (kind, d-tag). */
fun TagArray.dListCurations() = mapNotNull(DListCurationTag::parse).distinctBy { it.kind to it.dTag }

fun TagArray.dListCuration(
    kind: Int,
    dTag: String,
) = firstNotNullOfOrNull { tag -> DListCurationTag.parse(tag)?.takeIf { it.kind == kind && it.dTag == dTag } }

/**
 * Sets the blanket designation in place, keeping every other tag verbatim — 10040 is
 * replaceable, so whatever is dropped here is gone from the Map. Duplicates collapse onto the
 * first position.
 */
fun TagArray.replaceDListAssistant(designation: AssistantDesignation): TagArray = replaceFirstMatch(designation.toTagArray()) { AssistantDesignationTag.isTag(it) }

fun TagArray.removeDListAssistant(): TagArray = filterNot { AssistantDesignationTag.isTag(it) }.toTypedArray()

/** Adds or replaces the entry for the curation's (kind, d-tag), as [replaceDListAssistant]. */
fun TagArray.replaceDListCuration(entry: DListCuration): TagArray =
    replaceFirstMatch(entry.toTagArray()) { tag ->
        DListCurationTag.parse(tag)?.let { it.kind == entry.kind && it.dTag == entry.dTag } ?: false
    }

/** Revokes the empowerment. The header and its `b` stay on relays. */
fun TagArray.removeDListCuration(
    kind: Int,
    dTag: String,
): TagArray =
    filterNot { tag ->
        DListCurationTag.parse(tag)?.let { it.kind == kind && it.dTag == dTag } ?: false
    }.toTypedArray()

private inline fun TagArray.replaceFirstMatch(
    replacement: Array<String>,
    matches: (Array<String>) -> Boolean,
): TagArray {
    var replaced = false
    val out = ArrayList<Array<String>>(size + 1)
    forEach { tag ->
        if (matches(tag)) {
            if (!replaced) {
                out.add(replacement)
                replaced = true
            }
        } else {
            out.add(tag)
        }
    }
    if (!replaced) out.add(replacement)
    return out.toTypedArray()
}
