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
package com.vitorpamplona.quartz.concord.cord07Voice

import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The blind-broker token request (CORD-07 §2). A member proves Channel membership
 * to a stateless SFU broker by signing a NIP-98-style kind-27235 event with the
 * Channel's derived **voice signer key** — whose public key is the SFU room name,
 * so only members (who can derive it) can mint valid requests. The broker holds
 * no Community secret.
 *
 * The request rides an `Authorization: Concord <base64(event)>` header against
 * `GET /.well-known/concord/av/<voice_room>`.
 */
object ConcordBrokerToken {
    const val KIND = 27235 // NIP-98 HTTP auth
    const val AUTH_SCHEME = "Concord"
    const val TAG_URL = "u"
    const val TAG_METHOD = "method"

    /** The broker path for a voice room (the room = the voice signer's x-only pubkey hex). */
    fun wellKnownPath(voiceRoomHex: String): String = "/.well-known/concord/av/$voiceRoomHex"

    /**
     * Builds the kind-27235 auth event for [url]/[method], signed by the channel's
     * [voiceSigner] key (its public key is the voice room / SFU name).
     */
    fun buildAuthEvent(
        voiceSigner: GroupKey,
        url: String,
        createdAt: Long,
        method: String = "GET",
    ): Event {
        val signer = NostrSignerSync(KeyPair(privKey = voiceSigner.secretKey))
        return signer.signNormal(createdAt, KIND, arrayOf(arrayOf(TAG_URL, url), arrayOf(TAG_METHOD, method)), "")
    }

    /** The `Authorization` header value carrying a base64 of the signed auth [event]. */
    @OptIn(ExperimentalEncodingApi::class)
    fun authorizationHeader(event: Event): String = "$AUTH_SCHEME " + Base64.Default.encode(event.toJson().encodeToByteArray())
}
