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
package com.vitorpamplona.quartz.buzz.wpWorkspaceProfile

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz Set Workspace Profile command (NIP-WP, `kind:9033`): a regular, admin/owner-signed
 * relay command that sets (or, with an empty/absent value, clears) the workspace icon. The
 * content is empty; the single `icon` tag carries an `http(s)` or `data:image` URL.
 *
 * Ground truth: `buzz-core/src/kind.rs` (`RELAY_ADMIN_SET_WORKSPACE_PROFILE`) and
 * `buzz-relay/src/handlers/relay_admin.rs` (`validate_workspace_icon`).
 */
@Immutable
class SetWorkspaceProfileEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The workspace icon URL — the `icon` tag, or null when the command clears the icon. */
    fun icon() = tags.workspaceIcon()

    companion object {
        const val KIND = 9033

        /**
         * Builds a workspace-profile command template. A non-null, non-empty [icon] sets the
         * workspace icon; a null [icon] omits the tag (clearing the icon). Sign it with an
         * admin/owner key.
         */
        fun build(
            icon: String?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SetWorkspaceProfileEvent>.() -> Unit = {},
        ) = eventTemplate<SetWorkspaceProfileEvent>(KIND, "", createdAt) {
            icon?.let { icon(it) }
            initializer()
        }
    }
}
