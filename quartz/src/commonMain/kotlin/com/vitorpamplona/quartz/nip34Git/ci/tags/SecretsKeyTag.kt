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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * A coordinator's current secret-update recipient: a NIP-44 key generated separately from its
 * signing key, and the secret-inbox relays a Repository Secret Update (29846) is submitted to.
 * [recipient] is an encryption key, not a user, so it is never a graph edge.
 */
@Immutable
data class CiSecretsKey(
    val scheme: String,
    val recipient: HexKey,
    val inboxRelays: List<NormalizedRelayUrl>,
)

/**
 * Nostr CI `secrets-key` tag:
 * `["secrets-key", "nip44-v2", "<32-byte-xonly-pubkey>", "wss://inbox-1", "wss://inbox-2", ...]`.
 *
 * At least one inbox relay MUST be present; a tag without a usable one does not parse, because a
 * maintainer would have nowhere to send the update.
 */
class SecretsKeyTag {
    companion object {
        const val TAG_NAME = "secrets-key"
        const val NIP44_V2 = "nip44-v2"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): CiSecretsKey? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].length == 64 && Hex.isHex(tag[2])) { return null }
            val relays = (3 until tag.size).mapNotNull { RelayUrlNormalizer.normalizeHintOrNull(tag[it]) }
            ensure(relays.isNotEmpty()) { return null }
            return CiSecretsKey(tag[1], tag[2], relays)
        }

        fun assemble(key: CiSecretsKey) = arrayOf(TAG_NAME, key.scheme, key.recipient, *key.inboxRelays.map { it.url }.toTypedArray())
    }
}
