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
package com.vitorpamplona.quartz.experimental.trustedLists.treasureMap

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull

/**
 * Every Trusted List entry in a Treasure Map, generic and named alike. Named
 * entries are reserved, so a caller that intends to *act* on a delegation
 * wants [trustedListProvider] instead -- this is the display-everything view.
 */
fun TagArray.trustedListProviders() = mapNotNull(TrustedListProviderTag::parse)

/**
 * The generic entry delegating [kind], or null when the Map does not delegate
 * that kind.
 *
 * There is meant to be at most one generic entry per kind. Where duplicates
 * turn up in the wild the **first occurrence wins**, which is what
 * [fastFirstNotNullOfOrNull] gives us -- a fixed rule so that two readers of
 * the same Map resolve the same publisher.
 */
fun TagArray.trustedListProvider(kind: Int) =
    fastFirstNotNullOfOrNull { tag ->
        TrustedListProviderTag.parseGeneric(tag)?.takeIf { it.kind == kind }
    }

/**
 * Replaces the entry [provider] would occupy, preserving **every other tag
 * verbatim** -- 10040 is replaceable, so an update republishes the whole tag
 * set and anything dropped here is lost from the Map for good.
 *
 * What counts as "the same entry" is kind **and** name, the pair the first
 * element encodes: a generic write replaces the generic entry, a named write
 * replaces that name. Matching on kind alone would let a named write delete
 * the kind's generic delegation -- a different, live delegation, gone
 * irrecoverably -- while never finding its own entry to replace, so it would
 * also duplicate on every later call.
 *
 * The replacement keeps the old entry's position rather than moving it to the
 * end, so a Map does not reshuffle on every publisher switch. Redundant
 * entries for the same kind and name are collapsed onto that one: at most one
 * is the invariant, and a writer that has to touch the entry anyway is the
 * right place to settle a Map that arrived violating it.
 */
fun TagArray.replaceTrustedListProvider(provider: TrustedListProviderTag): TagArray {
    val replacement = provider.toTagArray()
    var replaced = false

    val out = ArrayList<Array<String>>(size + 1)
    forEach { tag ->
        if (isSameEntry(tag, provider.kind, provider.name)) {
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

/**
 * Drops the entry for [kind] and [name] -- the generic one by default --
 * leaving every other tag verbatim.
 */
fun TagArray.removeTrustedListProvider(
    kind: Int,
    name: String? = null,
): TagArray = filterNot { isSameEntry(it, kind, name) }.toTypedArray()

/**
 * Whether [tag] is the Trusted List entry addressed by [kind] and [name]. The
 * comparison goes through the parser rather than string-matching the first
 * element so that a tag we would not read is never one we silently delete.
 */
private fun isSameEntry(
    tag: Tag,
    kind: Int,
    name: String?,
): Boolean {
    val entry = TrustedListProviderTag.parse(tag) ?: return false
    return entry.kind == kind && entry.name == name
}
