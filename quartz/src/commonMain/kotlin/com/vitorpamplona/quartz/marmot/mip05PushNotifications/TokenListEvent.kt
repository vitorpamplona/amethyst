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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags.TokenTagData
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot Token List Response Event (MIP-05) — kind 448.
 *
 * Unsigned application message sent inside a GroupEvent (kind:445) in response
 * to a TokenRequestEvent (kind:447). Contains the responder's complete view
 * of all active encrypted device tokens in the group.
 *
 * Token tags include a leaf_index field (4th value) to identify which
 * MLS leaf owns each token. An "e" tag references the kind:447 event
 * this is responding to.
 *
 * Members SHOULD add random delay (0-2s) before responding.
 * MUST remain unsigned (no sig field) per MIP-03 security requirements.
 */
@Immutable
class TokenListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** All known encrypted tokens with their leaf indices */
    fun tokens() = tags.tokens()

    /** Event ID of the kind:447 request this responds to */
    fun requestEventId() = tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1)

    companion object {
        const val KIND = 448

        fun build(
            allTokens: List<TokenTagData>,
            requestEventId: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TokenListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            tokens(allTokens)
            add(arrayOf("e", requestEventId))
            initializer()
        }
    }
}
