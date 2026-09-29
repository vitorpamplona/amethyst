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
package com.vitorpamplona.quartz.nip34Git

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation

/**
 * NIP-34 names the repository an event is about in its `a` tags (one per maintainer's
 * announcement). Emits them as [Relation.REPOSITORY] and returns their authors, the repository
 * owners that [gitPeopleLinks] tells apart from the other `p` tags.
 */
internal fun LinkBuilder.repositoryLinks(tags: TagArray): Set<HexKey> {
    val owners = mutableSetOf<HexKey>()
    tags.fastForEach {
        if (it.size < 2 || it[0] != "a") return@fastForEach
        val address = LinkBuilder.normalizedAddress(it[1]) ?: return@fastForEach
        address(Relation.REPOSITORY, address, "a")
        val pubKeyStart = address.indexOf(':') + 1
        owners.add(address.substring(pubKeyStart, pubKeyStart + 64))
    }
    return owners
}

/**
 * NIP-34 `p` tags carry no marker: the repository owner, the root event's author and the
 * revision's author are told apart only by comparing each key with the pubkeys the `a` and `e`
 * tags name. A key can hold several of those roles at once; one that holds none is a plain
 * notification, [Relation.MENTION].
 */
internal fun LinkBuilder.gitPeopleLinks(
    tags: TagArray,
    owners: Set<HexKey>,
    rootAuthor: HexKey? = null,
    parentAuthor: HexKey? = null,
) = tags.fastForEach {
    if (it.size < 2 || it[0] != "p") return@fastForEach
    val key = LinkBuilder.normalizedHex(it[1]) ?: return@fastForEach
    var named = false
    if (key in owners) {
        user(Relation.REPOSITORY_OWNER, key, "p")
        named = true
    }
    if (key == rootAuthor) {
        user(Relation.ROOT_AUTHOR, key, "p")
        named = true
    }
    if (key == parentAuthor) {
        user(Relation.PARENT_AUTHOR, key, "p")
        named = true
    }
    if (!named) user(Relation.MENTION, key, "p")
}
