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
package com.vitorpamplona.quartz.nipXXPushNotifications

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.serialization.json.Json

/**
 * A control message from a user to a push-notification service, from the draft "NIP-XX Push
 * Notifications" that divine.video runs (divine-push-service, `docs/nip-xx-push-notifications.md`).
 *
 * Every kind in the draft has the same envelope: a `p` tag naming the service, an `app` tag naming
 * the application, and content that is NIP-44 ciphertext to the service's key. The service rejects
 * plaintext, and ignores events addressed to another service or older than its seven-day replay
 * window. Only the author and the service can read the payload; anyone else sees who talks to
 * which service, not what they said.
 */
@Immutable
abstract class PushServiceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig),
    LinkProvider {
    override fun isContentEncoded() = true

    /**
     * The push service the payload is encrypted to (the `p`), as marmot's token records name
     * theirs. The `app` tag is an application id, not a reference; the payload is private.
     */
    override fun links(): List<Link> = links { user(Relation.NOTIFICATION_SERVER, pushService(), "p") }

    fun pushService() = tags.pushService()

    fun app() = tags.app()

    fun canDecrypt(signer: NostrSigner) = counterpartyOf(signer.pubKey) != null

    /**
     * The other end of the NIP-44 conversation for [reader]: the author reads its own event back
     * through the service's key, the service reads it through the author's. Null for anyone else.
     */
    private fun counterpartyOf(reader: HexKey): HexKey? {
        val service = pushService() ?: return null
        return when (reader) {
            pubKey -> service
            service -> pubKey
            else -> null
        }
    }

    /** Throws [SignerExceptions.UnauthorizedDecryptionException] when [signer] is neither end. */
    protected suspend fun decryptContent(signer: NostrSigner): String {
        val counterparty = counterpartyOf(signer.pubKey) ?: throw SignerExceptions.UnauthorizedDecryptionException()
        return signer.nip44Decrypt(content, counterparty)
    }

    companion object {
        /**
         * The payloads are small JSON objects whose fields the service defines, so unknown keys
         * are expected. Absent optionals are left out rather than written as `null`.
         */
        internal val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }
    }
}
