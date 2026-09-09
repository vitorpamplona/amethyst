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
 * Marmot push token removal — kind 449
 * (`features/push-notifications.md`, "Removal").
 *
 * A removal names one device's record exactly: the `leaf_index` in each entry
 * is what stops a revocation from taking a sibling device's live token with it.
 * A winning removal does not just delete — it leaves a tombstone at its own
 * stamp, so a token list assembled before the removal cannot resurrect the
 * revoked token when it finally arrives.
 *
 * Unsigned, like every inner app payload; each entry carries its own
 * `owner_sig`.
 */
@Immutable
class TokenRemovalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun entries() = PushGossip.decodeRemovals(content)

    companion object {
        const val KIND = 449

        fun build(
            entries: List<PushRemovalEntry>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TokenRemovalEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, PushGossip.encodeRemovals(entries), createdAt) {
            addUnique(VersionTag.assemble())
            initializer()
        }
    }
}
