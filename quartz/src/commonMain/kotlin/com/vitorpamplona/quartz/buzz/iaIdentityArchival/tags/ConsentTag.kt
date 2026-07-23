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
package com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The NIP-IA `consent` tag: `["consent", "<path>", "<actor-pubkey-hex>"]`.
 *
 * Written only by the relay onto the archived/unarchived delta events (kinds 8002 /
 * 8003) to record which consent path authorized the mutation and which actor signed the
 * originating request. [path] is one of [PATH_SELF] (the target archived itself),
 * [PATH_OWNER] (a NIP-OA owner-of-agent attestation), or [PATH_ADMIN] (a relay
 * admin/owner). Ground truth: `buzz-relay/src/handlers/identity_archive.rs`
 * (`ConsentPath::as_str`) and `side_effects.rs::publish_nipia_delta`.
 */
object ConsentTag {
    const val TAG_NAME = "consent"

    const val PATH_SELF = "self"
    const val PATH_OWNER = "owner"
    const val PATH_ADMIN = "admin"

    fun match(tag: Tag) = tag.has(2) && tag[0] == TAG_NAME

    fun parse(tag: Array<String>): Consent? {
        ensure(tag.has(2)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        ensure(tag[2].isNotEmpty()) { return null }
        return Consent(tag[1], tag[2])
    }

    fun assemble(
        path: String,
        actorPubKey: HexKey,
    ) = arrayOf(TAG_NAME, path, actorPubKey)
}

/** The decoded content of a NIP-IA [ConsentTag]: the consent [path] and the [actorPubKey] that signed the request. */
data class Consent(
    val path: String,
    val actorPubKey: HexKey,
)
