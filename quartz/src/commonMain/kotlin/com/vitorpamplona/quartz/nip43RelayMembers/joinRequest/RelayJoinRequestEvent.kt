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
package com.vitorpamplona.quartz.nip43RelayMembers.joinRequest

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip70ProtectedEvts.protect
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-43 kind 28934: a request to join a relay. Must carry a NIP-70 `-` tag and
 * a `claim` tag with the invite code; the relay answers with an `OK`. Invite
 * codes are minted by the relay (NIP-86 `createclaim`), not requested with the
 * deprecated kind 28935.
 */
@Immutable
class RelayJoinRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun claim() = tags.claim()

    companion object {
        const val KIND = 28934

        fun build(
            claim: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayJoinRequestEvent>.() -> Unit = {},
        ): EventTemplate<RelayJoinRequestEvent> {
            require(claim.isNotBlank()) { "NIP-43 join requests require an invite code (claim)" }
            return eventTemplate(KIND, "", createdAt) {
                protect()
                claim(claim)
                initializer()
            }
        }
    }
}
