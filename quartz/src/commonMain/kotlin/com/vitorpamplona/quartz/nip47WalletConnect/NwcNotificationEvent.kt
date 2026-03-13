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
package com.vitorpamplona.quartz.nip47WalletConnect

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

@Immutable
class NwcNotificationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun clientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) clientPubKey() ?: pubKey else pubKey

    fun canDecrypt(signer: NostrSigner) = pubKey == signer.pubKey || clientPubKey() == signer.pubKey

    suspend fun decryptNotification(signer: NostrSigner): Notification {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()
        val jsonText = signer.nip44Decrypt(content, talkingWith(signer.pubKey))
        return OptimizedJsonMapper.fromJsonTo<Notification>(jsonText)
    }

    companion object {
        const val KIND = 23197
        const val LEGACY_KIND = 23196
        const val ALT = "Wallet notification"
    }
}
