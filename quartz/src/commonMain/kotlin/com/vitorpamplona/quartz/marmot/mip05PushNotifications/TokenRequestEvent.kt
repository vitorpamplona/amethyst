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
 * Marmot Token Request Event (MIP-05) — kind 447.
 *
 * Unsigned application message sent inside a GroupEvent (kind:445) when a device
 * joins a group, needs to refresh its token view, or has a token change.
 *
 * Includes the sender's own encrypted token in "token" tags to bootstrap
 * the device into the group's notification system immediately.
 *
 * The MLS leaf index is implicit from the MLS sender identity.
 * MUST remain unsigned (no sig field) per MIP-03 security requirements.
 */
@Immutable
class TokenRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Encrypted tokens included with this request (sender's own tokens) */
    fun tokens() = tags.tokens()

    companion object {
        const val KIND = 447

        fun build(
            ownTokens: List<TokenTagData>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TokenRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            tokens(ownTokens)
            initializer()
        }
    }
}
