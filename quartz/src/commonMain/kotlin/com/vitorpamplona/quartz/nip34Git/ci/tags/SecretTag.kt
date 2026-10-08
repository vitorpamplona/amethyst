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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * One effective repository secret, disclosed by name only — never by value.
 *
 * A secret accepted from a Repository Secret Update (29846) carries the maintainer that provisioned
 * it ([origin]) and that update's `created_at` ([acceptedUpdateAt]); [sealed] marks a value held only
 * as bunker-sealed ciphertext. An operator-provided secret carries the name alone.
 */
@Immutable
data class CiRepositorySecret(
    val name: String,
    val origin: HexKey?,
    val acceptedUpdateAt: Long?,
    val sealed: Boolean,
) {
    fun isOperatorProvided() = origin == null
}

/**
 * Nostr CI `secret` tag on a Coordinator Repository Status (39844), in one of three forms:
 * `["secret", "<name>"]` (operator), `["secret", "<name>", "<origin-pubkey>", "<created-at>"]`
 * (from Nostr) and the same with a trailing literal `"sealed"`.
 *
 * A tag that starts the Nostr form but has a malformed origin or timestamp does not parse: it is
 * not an operator secret either, and guessing would misattribute who provisioned it.
 */
class SecretTag {
    companion object {
        const val TAG_NAME = "secret"
        const val SEALED = "sealed"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): CiRepositorySecret? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            if (tag.size == 2) return CiRepositorySecret(tag[1], null, null, false)

            ensure(tag.has(3)) { return null }
            ensure(tag[2].length == 64 && Hex.isHex(tag[2])) { return null }
            val createdAt = tag[3].toLongOrNull() ?: return null
            return CiRepositorySecret(tag[1], tag[2], createdAt, tag.getOrNull(4) == SEALED)
        }

        fun assemble(secret: CiRepositorySecret): Array<String> {
            val origin = secret.origin
            val acceptedAt = secret.acceptedUpdateAt
            return when {
                origin == null || acceptedAt == null -> arrayOf(TAG_NAME, secret.name)
                secret.sealed -> arrayOf(TAG_NAME, secret.name, origin, acceptedAt.toString(), SEALED)
                else -> arrayOf(TAG_NAME, secret.name, origin, acceptedAt.toString())
            }
        }
    }
}
