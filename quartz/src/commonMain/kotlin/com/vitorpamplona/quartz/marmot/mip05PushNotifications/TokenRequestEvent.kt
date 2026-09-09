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
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot push token request / self-update — kind 447
 * (`features/push-notifications.md`, "Request and update").
 *
 * One kind carries two intents, told apart by the array alone: a non-empty
 * `tokens` array announces the sender's own current record, an empty one asks
 * everyone else to share theirs. An empty request changes no state anywhere, so
 * nothing has to distinguish them beyond counting.
 *
 * An unsigned Marmot app payload like every other inner event — it MUST NOT
 * carry a `sig`. Its authority lives entirely in each entry's `owner_sig`.
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
    /** The records this event announces; empty for a request. */
    fun entries() = PushGossip.decodeTokens(content)

    fun isRequest() = entries().isEmpty()

    companion object {
        const val KIND = 447

        fun build(
            entries: List<PushTokenEntry>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TokenRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, PushGossip.encodeTokens(entries), createdAt) {
            addUnique(VersionTag.assemble())
            initializer()
        }

        /** The empty form: "share your records with me." */
        fun buildRequest(
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TokenRequestEvent>.() -> Unit = {},
        ) = build(emptyList(), createdAt, initializer)
    }
}
