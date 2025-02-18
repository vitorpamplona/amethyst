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
package com.vitorpamplona.quartz.nip57Zaps

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync

class PrivateZapRequestBuilder {
    fun <T> signPrivateZapRequest(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        signer: NostrSignerSync,
    ): T? {
        if (signer.keyPair.privKey == null) return null

        val zappedEvent = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.let { it[1] }
        val userHex = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.let { it[1] } ?: return null

        // if it is a Zap for an Event, use event.id if not, use the user's pubkey
        val idToGeneratePrivateKey = zappedEvent ?: userHex

        val encryptionPrivateKey =
            PrivateZapEncryption.createEncryptionPrivateKey(
                signer.keyPair.privKey.toHexKey(),
                idToGeneratePrivateKey,
                createdAt,
            )

        val fullTagsNoAnon = tags.filter { t -> t.getOrNull(0) != "anon" }.toTypedArray()

        val privateEvent = LnZapPrivateEvent.create(signer, fullTagsNoAnon, content) ?: return null

        val noteJson = privateEvent.toJson()
        val encryptedContent =
            PrivateZapEncryption.encryptPrivateZapMessage(
                noteJson,
                encryptionPrivateKey,
                userHex.hexToByteArray(),
            )

        val newTags = tags.filter { t -> t.getOrNull(0) != "anon" } + listOf(arrayOf("anon", encryptedContent))
        val newContent = ""

        return NostrSignerSync(KeyPair(encryptionPrivateKey)).signNormal(createdAt, kind, newTags.toTypedArray(), newContent)
    }

    fun decryptZapEvent(
        event: LnZapRequestEvent,
        signer: NostrSignerSync,
    ): LnZapPrivateEvent? {
        if (signer.keyPair.privKey == null) return null

        val recipientPK = event.zappedAuthor().firstOrNull()
        val recipientPost = event.zappedPost().firstOrNull()
        val privateEvent =
            if (recipientPK == signer.pubKey) {
                // if the receiver is logged in, these are the params.
                val privateKeyToUse = signer.keyPair.privKey
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(privateKeyToUse, pubkeyToUse)
            } else {
                // if the sender is logged in, these are the params
                val altPubkeyToUse = recipientPK
                val altPrivateKeyToUse =
                    if (recipientPost != null) {
                        PrivateZapEncryption.createEncryptionPrivateKey(
                            signer.keyPair.privKey.toHexKey(),
                            recipientPost,
                            event.createdAt,
                        )
                    } else if (recipientPK != null) {
                        PrivateZapEncryption.createEncryptionPrivateKey(
                            signer.keyPair.privKey.toHexKey(),
                            recipientPK,
                            event.createdAt,
                        )
                    } else {
                        null
                    }

                try {
                    if (altPrivateKeyToUse != null && altPubkeyToUse != null) {
                        val altPubKeyFromPrivate = Nip01.pubKeyCreate(altPrivateKeyToUse).toHexKey()

                        if (altPubKeyFromPrivate == event.pubKey) {
                            val result = event.getPrivateZapEvent(altPrivateKeyToUse, altPubkeyToUse)

                            if (result == null) {
                                Log.w(
                                    "Private ZAP Decrypt",
                                    "Fail to decrypt Zap from ${event.id}",
                                )
                            }
                            result
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("Account", "Failed to create pubkey for ZapRequest ${event.id}", e)
                    null
                }
            }

        return privateEvent
    }
}
