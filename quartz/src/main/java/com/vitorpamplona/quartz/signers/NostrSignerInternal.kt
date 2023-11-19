package com.vitorpamplona.quartz.signers

import android.util.Log
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventFactory
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NostrSignerInternal(val keyPair: KeyPair): NostrSigner(keyPair.pubKey.toHexKey()) {
    override fun <T: Event> sign(
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
        onReady: (T)-> Unit
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
        tags: List<List<String>>,
    ): Boolean {
        return kind == LnZapRequestEvent.kind && tags.any { t -> t.size > 1 && t[0] == "anon" && t[1].isBlank() }
    }

    fun <T: Event> signNormal(
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
        onReady: (T)-> Unit
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
                sig
            ) as T // Must never crash
        )
    }

    override fun nip04Encrypt(decryptedContent: String, toPublicKey: HexKey, onReady: (String)-> Unit) {
        if (keyPair.privKey == null) return

        onReady(
            CryptoUtils.encryptNIP04(
                decryptedContent,
                keyPair.privKey,
                toPublicKey.hexToByteArray()
            )
        )
    }

    override fun nip04Decrypt(encryptedContent: String, fromPublicKey: HexKey, onReady: (String)-> Unit) {
        if (keyPair.privKey == null) return

        try {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(keyPair.privKey, fromPublicKey.hexToByteArray())

            onReady(CryptoUtils.decryptNIP04(encryptedContent, sharedSecret))
        } catch (e: Exception) {
            Log.w("NIP04Decrypt", "Error decrypting the message ${e.message} on ${encryptedContent}")
        }
    }

    override fun nip44Encrypt(decryptedContent: String, toPublicKey: HexKey, onReady: (String)-> Unit) {
        if (keyPair.privKey == null) return

        val sharedSecret = CryptoUtils.getSharedSecretNIP44(keyPair.privKey, toPublicKey.hexToByteArray())

        onReady(
            encodeNIP44(
                CryptoUtils.encryptNIP44(
                    decryptedContent,
                    sharedSecret
                )
            )
        )
    }

    override fun nip44Decrypt(encryptedContent: String, fromPublicKey: HexKey, onReady: (String)-> Unit) {
        if (keyPair.privKey == null) return

        val toDecrypt = decodeNIP44(encryptedContent) ?: return

        when (toDecrypt.v) {
            Nip44Version.NIP04.versionCode -> CryptoUtils.decryptNIP04(toDecrypt, keyPair.privKey, fromPublicKey.hexToByteArray())
            Nip44Version.NIP44.versionCode -> CryptoUtils.decryptNIP44(toDecrypt, keyPair.privKey, fromPublicKey.hexToByteArray())
            else -> null
        }?.let {
            onReady(it)
        }
    }

    private fun <T> signPrivateZap(
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
        onReady: (T)-> Unit
    ) {
        if (keyPair.privKey == null) return

        val zappedEvent = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.let { it[1] }
        val userHex = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.let { it[1] } ?: return

        // if it is a Zap for an Event, use event.id if not, use the user's pubkey
        val idToGeneratePrivateKey = zappedEvent ?: userHex

        val encryptionPrivateKey =
            LnZapRequestEvent.createEncryptionPrivateKey(keyPair.privKey.toHexKey(), idToGeneratePrivateKey, createdAt)

        val fullTagsNoAnon = tags.filter { t -> t.getOrNull(0) != "anon" }

        LnZapPrivateEvent.create(this, fullTagsNoAnon, content) {
            val noteJson = it.toJson()
            val encryptedContent = LnZapRequestEvent.encryptPrivateZapMessage(
                noteJson,
                encryptionPrivateKey,
                userHex.hexToByteArray()
            )

            val newTags = tags.filter { t -> t.getOrNull(0) != "anon" } + listOf(listOf("anon", encryptedContent))
            val newContent = ""

            NostrSignerInternal(KeyPair(encryptionPrivateKey)).signNormal(createdAt, kind, newTags, newContent, onReady)
        }
    }

    override fun decryptZapEvent(event: LnZapRequestEvent, onReady: (Event)-> Unit) {
        if (keyPair.privKey == null) return

        val recipientPK = event.zappedAuthor().firstOrNull()
        val recipientPost = event.zappedPost().firstOrNull()
        val privateEvent = if (recipientPK == pubKey) {
            // if the receiver is logged in, these are the params.
            val privateKeyToUse = keyPair.privKey
            val pubkeyToUse = event.pubKey

            event.getPrivateZapEvent(privateKeyToUse, pubkeyToUse)
        } else {
            // if the sender is logged in, these are the params
            val altPubkeyToUse = recipientPK
            val altPrivateKeyToUse = if (recipientPost != null) {
                LnZapRequestEvent.createEncryptionPrivateKey(
                    keyPair.privKey.toHexKey(),
                    recipientPost,
                    event.createdAt
                )
            } else if (recipientPK != null) {
                LnZapRequestEvent.createEncryptionPrivateKey(
                    keyPair.privKey.toHexKey(),
                    recipientPK,
                    event.createdAt
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
                                "Fail to decrypt Zap from ${event.id}"
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

        privateEvent?.let {
            onReady(it)
        }
    }
}