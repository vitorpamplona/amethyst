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
package com.vitorpamplona.quartz.nip29RelayGroups.moderation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GroupPutUserEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    // `p` tags carry roles, not relays, after the pubkey: there is never a hint.
    override fun pubKeyHints(): List<PubKeyHint> = emptyList()

    override fun linkedPubKeys(): List<HexKey> = userPubKeys()

    fun groupId() = tags.groupId()

    fun userPubKeys() = tags.userPubKeys()

    fun previousEvents() = tags.previousEvents()

    companion object {
        const val KIND = 9000

        /**
         * NIP-29 put-user. The roles ride inside each `p` tag (`["p", pubkey, role, …]`), which is
         * what relay29 reads.
         *
         * [buzzRole] additionally emits a top-level `["role", …]` tag. Buzz reads **only** that —
         * `extract_tag_value(event, "role")` — so a promotion must carry it. Without it Buzz makes
         * **no role change**: an existing member keeps their role and only a newcomer defaults to
         * `member`, which is what a plain "add" wants. Changing an active member's role (either way)
         * is owner/admin-only, and the last owner can't be demoted. Its vocabulary is also its
         * own (`owner`/`admin`/`member`/`guest`/`bot`, no moderator); an unparseable role fails the
         * whole handler, so callers map to Buzz's set before passing it here. Harmless on relay29,
         * which ignores the extra tag.
         */
        fun build(
            groupId: String,
            pubKeysWithRoles: List<Pair<HexKey, List<String>>>,
            previousEvents: List<String> = emptyList(),
            buzzRole: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupPutUserEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            groupId(groupId)
            pubKeysWithRoles.forEach { (pubKey, roles) ->
                userPubKeyWithRoles(pubKey, roles)
            }
            buzzRole?.let { add(arrayOf("role", it)) }
            previous(previousEvents)
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to GroupPutUserEvent. NIP-29 group events carry the Group prefix to keep them apart from NIP-43 relay membership events.",
    ReplaceWith("GroupPutUserEvent", "com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent"),
)
typealias PutUserEvent = GroupPutUserEvent
