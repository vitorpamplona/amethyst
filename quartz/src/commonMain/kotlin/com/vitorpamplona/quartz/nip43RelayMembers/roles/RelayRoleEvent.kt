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
package com.vitorpamplona.quartz.nip43RelayMembers.roles

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip70ProtectedEvts.protect
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-43 kind 33534: a role the relay defines and may assign to members.
 *
 * Signed by the relay's NIP-11 `self` pubkey and protected (NIP-70 `-` tag).
 * The `d` tag is the role id — the value a kind 13534 `member` tag lists after
 * the pubkey. `label`, `description`, `color` (a hue, 0..360) and `order`
 * (display-only sort key) are optional.
 */
@Immutable
class RelayRoleEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    /** The role's human-facing name and description; the id, hue and order are machine data. */
    override fun indexableContent() = listOfNotNull(label(), description()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(label())) return
        visitor.visit(description())
    }

    fun roleId() = dTag()

    fun label() = tags.roleLabel()

    fun description() = tags.roleDescription()

    /** Hue in `0..360`, or null when absent or out of range. */
    fun color() = tags.roleColor()

    fun order() = tags.roleOrder()

    fun role() =
        RelayRole(
            id = roleId(),
            label = label(),
            description = description(),
            color = color(),
            order = order(),
        )

    companion object {
        const val KIND = 33534

        fun build(
            role: RelayRole,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayRoleEvent>.() -> Unit = {},
        ) = eventTemplate<RelayRoleEvent>(KIND, "", createdAt) {
            protect()
            dTag(role.id)
            role.label?.let { roleLabel(it) }
            role.description?.let { roleDescription(it) }
            role.color?.let {
                require(RelayRole.isValidHue(it)) { "role color must be a hue between 0 and 360, got $it" }
                roleColor(it)
            }
            role.order?.let { roleOrder(it) }
            initializer()
        }
    }
}
