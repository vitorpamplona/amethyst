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
package com.vitorpamplona.quartz.nip90Dvms

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Stable
@Immutable
class NIP90TextGenDiscoveryRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 5050
        const val ALT = "NIP90 Text Generation request"

        fun create(
            dvmPublicKey: HexKey,
            forUser: HexKey,
            relays: Set<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (NIP90TextGenDiscoveryRequestEvent) -> Unit,
        ) {
            val content = ""
            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("p", dvmPublicKey))
            tags.add(AltTag.assemble(ALT))
            tags.add(arrayOf("relays") + relays.toTypedArray())
            tags.add(arrayOf("param", "max_results", "200"))
            tags.add(arrayOf("param", "user", forUser))
            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
} 
