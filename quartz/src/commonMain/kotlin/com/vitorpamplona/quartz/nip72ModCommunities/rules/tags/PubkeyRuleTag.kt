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
package com.vitorpamplona.quartz.nip72ModCommunities.rules.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-9A `p` tag: per-pubkey allow/deny override with optional role.
 *
 * Form: `["p", "<hex>", "<allow|deny>", "<role?>"]`
 *
 * `deny` overrides any `allow` and any other rule.
 */
@Immutable
data class PubkeyRuleTag(
    val pubkey: HexKey,
    val policy: Policy,
    val role: String?,
) {
    enum class Policy { ALLOW, DENY }

    fun toTagArray() = assemble(pubkey, policy, role)

    companion object {
        const val TAG_NAME = "p"
        const val ALLOW = "allow"
        const val DENY = "deny"

        fun parse(tag: Array<String>): PubkeyRuleTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val policy =
                when (tag[2]) {
                    ALLOW -> Policy.ALLOW
                    DENY -> Policy.DENY
                    else -> return null
                }

            return PubkeyRuleTag(tag[1], policy, tag.getOrNull(3)?.takeIf { it.isNotEmpty() })
        }

        fun assemble(
            pubkey: HexKey,
            policy: Policy,
            role: String?,
        ) = arrayOfNotNull(
            TAG_NAME,
            pubkey,
            if (policy == Policy.ALLOW) ALLOW else DENY,
            role,
        )
    }
}
