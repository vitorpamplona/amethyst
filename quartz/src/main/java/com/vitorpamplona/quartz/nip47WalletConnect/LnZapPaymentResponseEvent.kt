/**
 * Copyright (c) 2024 Vitor Pamplona
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

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class LnZapPaymentResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    // Once one of an app user decrypts the payment, all users else can see it.
    @Transient private var response: Response? = null

    override fun countMemory(): Long = super.countMemory() + pointerSizeInBytes + (response?.countMemory() ?: 0)

    fun requestAuthor() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun requestId() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) requestAuthor() ?: pubKey else pubKey

    private fun plainContent(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        try {
            signer.decrypt(content, talkingWith(signer.pubKey)) { content -> onReady(content) }
        } catch (e: Exception) {
            Log.w("PrivateDM", "Error decrypting the message ${e.message}")
        }
    }

    fun response(
        signer: NostrSigner,
        onReady: (Response) -> Unit,
    ) {
        response?.let {
            onReady(it)
            return
        }

        try {
            if (content.isNotEmpty()) {
                plainContent(signer) {
                    EventMapper.mapper.readValue(it, Response::class.java)?.let {
                        response = it
                        onReady(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LnZapPaymentResponseEvent", "Can't parse content as a payment response: $content", e)
        }
    }

    companion object {
        const val KIND = 23195
        const val ALT = "Zap payment response"
    }
}
