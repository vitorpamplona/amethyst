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
package com.vitorpamplona.quartz.signers

import android.util.Log
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventFactory
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent

class NostrSignerSync(
    val keyPair: KeyPair,
    val pubKey: HexKey = keyPair.pubKey.toHexKey(),
) {
    fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T? {
        if (keyPair.privKey == null) return null

        return if (isUnsignedPrivateZapEvent(kind, tags)) {
            // this is a private zap
            signPrivateZap(createdAt, kind, tags, content)
        } else {
            signNormal(createdAt, kind, tags, content)
        }
    }

    fun isUnsignedPrivateZapEvent(
        kind: Int,
        tags: Array<Array<String>>,
    ): Boolean =
        kind == LnZapRequestEvent.KIND &&
            tags.any { t -> t.size > 1 && t[0] == "anon" && t[1].isBlank() }

    fun <T : Event> signNormal(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T? {
        if (keyPair.privKey == null) return null

        val id = Event.generateIdBytes(pubKey, createdAt, kind, tags, content)
        val sig = CryptoUtils.sign(id, keyPair.privKey).toHexKey()

        return EventFactory.create(
            id.toHexKey(),
            pubKey,
            createdAt,
            kind,
            tags,
            content,
            sig,
        ) as T
    }

    fun nip04Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return CryptoUtils.encryptNIP04(
            decryptedContent,
            keyPair.privKey,
            toPublicKey.hexToByteArray(),
        )
    }

    fun nip04Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return try {
            val sharedSecret =
                CryptoUtils.getSharedSecretNIP04(keyPair.privKey, fromPublicKey.hexToByteArray())

            CryptoUtils.decryptNIP04(encryptedContent, sharedSecret)
        } catch (e: Exception) {
            Log.w("NIP04Decrypt", "Error decrypting the message ${e.message} on $encryptedContent")
            null
        }
    }

    fun nip44Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return CryptoUtils
            .encryptNIP44(
                decryptedContent,
                keyPair.privKey,
                toPublicKey.hexToByteArray(),
            ).encodePayload()
    }

    fun nip44Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return CryptoUtils
            .decryptNIP44(
                payload = encryptedContent,
                privateKey = keyPair.privKey,
                pubKey = fromPublicKey.hexToByteArray(),
            )
    }

    private fun <T> signPrivateZap(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T? {
        if (keyPair.privKey == null) return null

        val zappedEvent = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.let { it[1] }
        val userHex = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.let { it[1] } ?: return null

        // if it is a Zap for an Event, use event.id if not, use the user's pubkey
        val idToGeneratePrivateKey = zappedEvent ?: userHex

        val encryptionPrivateKey =
            LnZapRequestEvent.createEncryptionPrivateKey(
                keyPair.privKey.toHexKey(),
                idToGeneratePrivateKey,
                createdAt,
            )

        val fullTagsNoAnon = tags.filter { t -> t.getOrNull(0) != "anon" }.toTypedArray()

        val privateEvent = LnZapPrivateEvent.create(this, fullTagsNoAnon, content) ?: return null

        val noteJson = privateEvent.toJson()
        val encryptedContent =
            LnZapRequestEvent.encryptPrivateZapMessage(
                noteJson,
                encryptionPrivateKey,
                userHex.hexToByteArray(),
            )

        val newTags =
            tags.filter { t -> t.getOrNull(0) != "anon" } + listOf(arrayOf("anon", encryptedContent))
        val newContent = ""

        return NostrSignerSync(KeyPair(encryptionPrivateKey)).signNormal(createdAt, kind, newTags.toTypedArray(), newContent)
    }

    fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent? {
        if (keyPair.privKey == null) return null

        val recipientPK = event.zappedAuthor().firstOrNull()
        val recipientPost = event.zappedPost().firstOrNull()
        val privateEvent =
            if (recipientPK == pubKey) {
                // if the receiver is logged in, these are the params.
                val privateKeyToUse = keyPair.privKey
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(privateKeyToUse, pubkeyToUse)
            } else {
                // if the sender is logged in, these are the params
                val altPubkeyToUse = recipientPK
                val altPrivateKeyToUse =
                    if (recipientPost != null) {
                        LnZapRequestEvent.createEncryptionPrivateKey(
                            keyPair.privKey.toHexKey(),
                            recipientPost,
                            event.createdAt,
                        )
                    } else if (recipientPK != null) {
                        LnZapRequestEvent.createEncryptionPrivateKey(
                            keyPair.privKey.toHexKey(),
                            recipientPK,
                            event.createdAt,
                        )
                    } else {
                        null
                    }

                try {
                    if (altPrivateKeyToUse != null && altPubkeyToUse != null) {
                        val altPubKeyFromPrivate = CryptoUtils.pubkeyCreate(altPrivateKeyToUse).toHexKey()

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
