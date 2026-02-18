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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.core.Event.Companion.fromJson
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

class PrivateZapRequestBuilder {
    fun <T> signPrivateZapRequest(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        signer: NostrSignerSync,
    ): T {
        if (signer.keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        val zappedEvent = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.let { it[1] }
        val userHex = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.let { it[1] }

        require(userHex != null) { "A user is required when creating private zaps" }

        // if it is a Zap for an Event, use event.id if not, use the user's pubkey
        val idToGeneratePrivateKey = zappedEvent ?: userHex

        val encryptionPrivateKey =
            PrivateZapEncryption.createEncryptionPrivateKey(
                signer.keyPair.privKey.toHexKey(),
                idToGeneratePrivateKey,
                createdAt,
            )

        val fullTagsNoAnon = tags.filter { t -> t.getOrNull(0) != "anon" }.toTypedArray()

        val privateEvent = LnZapPrivateEvent.create(signer, fullTagsNoAnon, content)

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
    ): LnZapPrivateEvent {
        if (signer.keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        val recipientPK = event.zappedAuthor().firstOrNull()
        val recipientPost = event.zappedPost().firstOrNull()
        val privateEvent =
            if (recipientPK == signer.pubKey) {
                // if the receiver is logged in, these are the params.
                decryptAnonTag(event.getAnonTag(), signer.keyPair.privKey, event.pubKey)
            } else {
                // if the sender is logged in, these are the params
                val altPubkeyToUse = recipientPK
                val myPrivateKeyForThisEvent =
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
                        throw SignerExceptions.CouldNotPerformException("Couldn't find a secret to use. The private zap is neither a post nor an author zap")
                    }

                try {
                    if (altPubkeyToUse != null) {
                        val altPubKeyFromPrivate = Nip01Crypto.pubKeyCreate(myPrivateKeyForThisEvent).toHexKey()

                        if (altPubKeyFromPrivate == event.pubKey) {
                            // the sender is logged in.
                            decryptAnonTag(event.getAnonTag(), myPrivateKeyForThisEvent, altPubkeyToUse)
                        } else {
                            throw SignerExceptions.CouldNotPerformException("This private zap cannot be decrypted by this key.")
                        }
                    } else {
                        throw SignerExceptions.CouldNotPerformException("Recipient pubkey not found.")
                    }
                } catch (e: Exception) {
                    throw SignerExceptions.CouldNotPerformException("Failed to create pubkey for ZapRequest ${event.id}. ${e.message}")
                }
            }

        return privateEvent
    }

    fun decryptAnonTag(
        encNote: String,
        privateKey: ByteArray,
        pubKey: HexKey,
    ): LnZapPrivateEvent =
        try {
            val note = PrivateZapEncryption.decryptPrivateZapMessage(encNote, privateKey, pubKey.hexToByteArray())
            val decryptedEvent = fromJson(note)
            if (decryptedEvent.kind == 9733) {
                decryptedEvent as LnZapPrivateEvent
            } else {
                throw IllegalStateException("The decrypted event is not a private zap.")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Could not decrypt private zap. ${e.message}")
        }
}
