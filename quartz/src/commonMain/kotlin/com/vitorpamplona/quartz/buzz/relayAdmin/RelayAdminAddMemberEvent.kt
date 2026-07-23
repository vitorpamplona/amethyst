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
package com.vitorpamplona.quartz.buzz.relayAdmin

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.relayAdmin.tags.RoleTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-43 add-member admin command (`kind:9030`): admin/owner-signed, asks the relay
 * to add [target] to the relay member list at an optional [role] (default `member`; only an
 * owner may grant `admin`, and `owner` is rejected — use [RelayAdminChangeRoleEvent]). The
 * tenant is bound by the connection host, so there is no `h` tag; `content` is empty. The
 * relay validates + executes the command and never stores it. Ground truth:
 * `buzz-relay/src/handlers/relay_admin.rs` (kind 9030).
 */
@Immutable
class RelayAdminAddMemberEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The member being added — the single `p` tag. */
    fun target() = tags.relayAdminTarget()

    /** The requested `role`, if present (absent means the relay defaults to `member`). */
    fun role() = tags.relayAdminRole()

    companion object {
        const val KIND = 9030

        fun build(
            target: HexKey,
            role: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayAdminAddMemberEvent>.() -> Unit = {},
        ) = eventTemplate<RelayAdminAddMemberEvent>(KIND, "", createdAt) {
            addUnique(PTag.assemble(target, null))
            role?.let { addUnique(RoleTag.assemble(it)) }
            initializer()
        }
    }
}
