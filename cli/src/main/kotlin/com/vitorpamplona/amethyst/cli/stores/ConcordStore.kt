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
package com.vitorpamplona.amethyst.cli.stores

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.SecureFileIO
import java.io.File

/**
 * A joined/created Concord community persisted locally so later `send`/`read`/
 * `channels` runs can re-derive its planes. Holds the community's secrets, so the
 * file is written 0600 via [SecureFileIO].
 */
data class StoredCommunity(
    val name: String = "",
    val communityId: String = "",
    val owner: String = "",
    val ownerSalt: String = "",
    val root: String = "",
    val rootEpoch: Long = 0,
    val generalChannelId: String = "",
    val relays: List<String> = emptyList(),
    // Past access roots kept per epoch (CORD-06 Refounding rotates the root). Lets `read --epoch <n>`
    // re-derive a prior epoch's Chat Plane to reach pre-refounding history. Populated by `import`.
    val heldRoots: List<StoredHeldRoot> = emptyList(),
)

/** A past community_root for a specific epoch, mirroring quartz `HeldRoot`. */
data class StoredHeldRoot(
    val epoch: Long = 0,
    val root: String = "",
)

/**
 * File-backed list of the account's Concord communities at `~/.amy/<account>/
 * concord.json`. Reloaded per run (no in-process cache), matching Amy's
 * stateless-per-invocation model.
 */
class ConcordStore(
    private val file: File,
) {
    fun load(): List<StoredCommunity> =
        if (file.exists()) {
            runCatching { Output.mapper.readValue<List<StoredCommunity>>(file.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    fun save(list: List<StoredCommunity>) = SecureFileIO.writeTextAtomic(file, Output.mapper.writeValueAsString(list))

    /** Insert or replace by community id, keyed on the self-certifying id. */
    fun upsert(community: StoredCommunity) {
        val next = load().filterNot { it.communityId == community.communityId } + community
        save(next)
    }

    /** Resolve a user-supplied handle: exact name, exact id, or a unique id/name prefix. */
    fun find(handle: String): StoredCommunity? {
        val all = load()
        return all.firstOrNull { it.name == handle || it.communityId == handle }
            ?: all.singleOrNull { it.communityId.startsWith(handle) || it.name.startsWith(handle) }
    }
}
