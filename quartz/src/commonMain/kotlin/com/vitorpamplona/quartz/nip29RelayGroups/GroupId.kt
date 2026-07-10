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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * The identity of a NIP-29 relay-based group. A group is NOT global: its id is
 * only unique within its host relay, so a group is always the pair of the host
 * relay and the group id (the `h` tag on its events, the `d` tag on its kind
 * 39000 metadata). Mirrors [com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId].
 */
data class GroupId(
    val id: String,
    val relayUrl: NormalizedRelayUrl,
) : Comparable<GroupId> {
    fun toKey() = "$id@${relayUrl.url}"

    fun toDisplayKey() = "$id@${relayUrl.displayUrl()}"

    override fun compareTo(other: GroupId): Int {
        val result = id.compareTo(other.id)
        return if (result == 0) {
            relayUrl.url.compareTo(other.relayUrl.url)
        } else {
            result
        }
    }
}
