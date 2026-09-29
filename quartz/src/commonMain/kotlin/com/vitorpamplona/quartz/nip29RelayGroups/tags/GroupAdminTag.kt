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
package com.vitorpamplona.quartz.nip29RelayGroups.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.graph.props.RoleProps
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-29's `["p", <pubkey>, <role>…]`: a user and the roles they hold. The 39001 admin list
 * states it, and the 9000 put-user request uses the same shape to grant them.
 */
@Stable
class GroupAdminTag(
    val pubKey: HexKey,
    val roles: List<String>,
) {
    /** The roles as a link's qualifier; none is no props. */
    fun linkProps() = RoleProps(roles)

    companion object {
        const val TAG_NAME = "p"

        fun parse(tag: Array<String>): GroupAdminTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            val roles =
                (2 until tag.size).mapNotNull { i ->
                    tag[i].ifEmpty { null }
                }
            return GroupAdminTag(tag[1], roles)
        }

        fun assemble(
            pubKey: HexKey,
            roles: List<String>,
        ) = arrayOf(TAG_NAME, pubKey, *roles.toTypedArray())

        fun assemble(admins: List<GroupAdminTag>) = admins.map { assemble(it.pubKey, it.roles) }
    }
}
