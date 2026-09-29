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
package com.vitorpamplona.quartz.nip43RelayMembers.list.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.links.props.MemberProps
import com.vitorpamplona.quartz.utils.ensure

/**
 * A kind 13534 entry: the member's pubkey plus the NIP-43 role ids (kind
 * 33534 `d` tags) assigned to them, in tag order. [roles] is empty for a
 * member without roles, which is also what pre-roles relays publish.
 */
@Immutable
data class RelayMember(
    val pubKey: HexKey,
    val roles: List<String> = emptyList(),
) {
    /** The role ids as a link's qualifier; none is no props. */
    fun linkProps() = MemberProps(roles = roles)
}

/**
 * NIP-43 `["member", <pubkey>, <role-id>...]`. Role ids after the pubkey are
 * optional: [parse] ignores them (backward compatible) and [parseMember]
 * returns them.
 */
class MemberTag {
    companion object {
        const val TAG_NAME = "member"

        fun parse(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseMember(tag: Array<String>): RelayMember? {
            val pubKey = parse(tag) ?: return null
            if (tag.size <= 2) return RelayMember(pubKey)
            val roles = ArrayList<String>(tag.size - 2)
            for (i in 2 until tag.size) {
                val role = tag[i]
                if (role.isNotEmpty() && role !in roles) roles.add(role)
            }
            return RelayMember(pubKey, roles)
        }

        fun assemble(pubKey: HexKey) = arrayOf(TAG_NAME, pubKey)

        fun assemble(
            pubKey: HexKey,
            roles: List<String>,
        ): Array<String> = arrayOf(TAG_NAME, pubKey) + roles

        fun assemble(member: RelayMember) = assemble(member.pubKey, member.roles)

        fun assemble(pubKeys: List<HexKey>) = pubKeys.map { assemble(it) }
    }
}
