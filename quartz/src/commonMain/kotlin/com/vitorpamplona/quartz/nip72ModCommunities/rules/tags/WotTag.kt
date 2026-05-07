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
package com.vitorpamplona.quartz.nip72ModCommunities.rules.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-9A `wot` tag: optional web-of-trust gate.
 *
 * Form: `["wot", "<root-pubkey>", "<depth>"]`
 *
 * Posts are allowed only if the author is reachable from `<root-pubkey>` through
 * follow lists within `<depth>` hops. Multiple `wot` tags act as OR (any one
 * passing is enough). The lookup mechanism is unspecified: clients MAY use NIP-02
 * follow lists, NIP-85 trusted assertions, or any other source.
 */
@Immutable
data class WotTag(
    val rootPubkey: HexKey,
    val depth: Int,
) {
    fun toTagArray() = assemble(rootPubkey, depth)

    companion object {
        const val TAG_NAME = "wot"

        fun parse(tag: Array<String>): WotTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val depth = tag[2].toIntOrNull()?.takeIf { it > 0 } ?: return null

            return WotTag(tag[1], depth)
        }

        fun assemble(
            rootPubkey: HexKey,
            depth: Int,
        ) = arrayOf(TAG_NAME, rootPubkey, depth.toString())
    }
}
