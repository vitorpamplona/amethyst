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

class NostrSignerInternal(
    val keyPair: KeyPair,
) : NostrSigner(keyPair.pubKey.toHexKey()) {
    override fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        if (isUnsignedPrivateEvent(kind, tags)) {
            // this is a private zap
            signPrivateZap(createdAt, kind, tags, content, onReady)
        } else {
            signNormal(createdAt, kind, tags, content, onReady)
        }
    }

    fun isUnsignedPrivateEvent(
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
        onReady: (T) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        val id = Event.generateId(pubKey, createdAt, kind, tags, content)
        val sig = CryptoUtils.sign(id, keyPair.privKey).toHexKey()

        onReady(
            EventFactory.create(
                id.toHexKey(),
                pubKey,
                createdAt,
                kind,
                tags,
                content,
                sig,
            ) as T,
        )
    }

    override fun nip04Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        onReady(
            CryptoUtils.encryptNIP04(
                decryptedContent,
                keyPair.privKey,
                toPublicKey.hexToByteArray(),
            ),
        )
    }

    override fun nip04Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        try {
            val sharedSecret =
                CryptoUtils.getSharedSecretNIP04(keyPair.privKey, fromPublicKey.hexToByteArray())

            onReady(CryptoUtils.decryptNIP04(encryptedContent, sharedSecret))
        } catch (e: Exception) {
            Log.w("NIP04Decrypt", "Error decrypting the message ${e.message} on $encryptedContent")
        }
    }

    override fun nip44Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        onReady(
            CryptoUtils
                .encryptNIP44(
                    decryptedContent,
                    keyPair.privKey,
                    toPublicKey.hexToByteArray(),
                ).encodePayload(),
        )
    }

    override fun nip44Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        CryptoUtils
            .decryptNIP44(
                payload = encryptedContent,
                privateKey = keyPair.privKey,
                pubKey = fromPublicKey.hexToByteArray(),
            )?.let { onReady(it) }
    }

    private fun <T> signPrivateZap(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    ) {
        if (keyPair.privKey == null) return

        val zappedEvent = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.let { it[1] }
        val userHex = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.let { it[1] } ?: return

        // if it is a Zap for an Event, use event.id if not, use the user's pubkey
        val idToGeneratePrivateKey = zappedEvent ?: userHex

        val encryptionPrivateKey =
            LnZapRequestEvent.createEncryptionPrivateKey(
                keyPair.privKey.toHexKey(),
                idToGeneratePrivateKey,
                createdAt,
            )

        val fullTagsNoAnon = tags.filter { t -> t.getOrNull(0) != "anon" }.toTypedArray()

        LnZapPrivateEvent.create(this, fullTagsNoAnon, content) {
            val noteJson = it.toJson()
            val encryptedContent =
                LnZapRequestEvent.encryptPrivateZapMessage(
                    noteJson,
                    encryptionPrivateKey,
                    userHex.hexToByteArray(),
                )

            val newTags =
                tags.filter { t -> t.getOrNull(0) != "anon" } + listOf(arrayOf("anon", encryptedContent))
            val newContent = ""

            NostrSignerInternal(KeyPair(encryptionPrivateKey))
                .signNormal(createdAt, kind, newTags.toTypedArray(), newContent, onReady)
        }
    }

    override fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (LnZapPrivateEvent) -> Unit,
    ) {
        if (keyPair.privKey == null) return

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

        privateEvent?.let { onReady(it) }
    }
}
