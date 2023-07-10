package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.*
import nostr.postr.Bech32
import nostr.postr.Utils
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Immutable
class LnZapRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    @Transient
    private var privateZapEvent: Event? = null

    fun zappedPost() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    fun zappedAuthor() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    fun isPrivateZap() = tags.any { t -> t.size >= 2 && t[0] == "anon" && t[1].isNotBlank() }

    fun getPrivateZapEvent(loggedInUserPrivKey: ByteArray, pubKey: HexKey): Event? {
        if (privateZapEvent != null) return privateZapEvent

        val anonTag = tags.firstOrNull { t -> t.size >= 2 && t[0] == "anon" }
        if (anonTag != null) {
            val encnote = anonTag[1]
            if (encnote.isNotBlank()) {
                try {
                    val note = decryptPrivateZapMessage(encnote, loggedInUserPrivKey, pubKey.hexToByteArray())
                    val decryptedEvent = fromJson(note)
                    if (decryptedEvent.kind == 9733) {
                        privateZapEvent = decryptedEvent
                        return privateZapEvent
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return null
    }

    companion object {
        const val kind = 9734

        fun create(
            originalNote: EventInterface,
            relays: Set<String>,
            privateKey: ByteArray,
            pollOption: Int?,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            var content = message
            var privkey = privateKey
            var pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", originalNote.pubKey()),
                listOf("relays") + relays
            )
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(listOf(POLL_OPTION, pollOption.toString()))
            }
            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                tags = tags + listOf(listOf("anon", ""))
                privkey = Utils.privkeyCreate()
                pubKey = Utils.pubkeyCreate(privkey).toHexKey()
            } else if (zapType == LnZapEvent.ZapType.PRIVATE) {
                var encryptionPrivateKey = createEncryptionPrivateKey(privateKey.toHexKey(), originalNote.id(), createdAt)
                var noteJson = (create(privkey, 9733, listOf(tags[0], tags[1]), message)).toJson()
                var encryptedContent = encryptPrivateZapMessage(noteJson, encryptionPrivateKey, originalNote.pubKey().hexToByteArray())
                tags = tags + listOf(listOf("anon", encryptedContent))
                content = "" // make sure public content is empty, as the content is encrypted
                privkey = encryptionPrivateKey // sign event with generated privkey
                pubKey = Utils.pubkeyCreate(encryptionPrivateKey).toHexKey() // updated event with according pubkey
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create(
            userHex: String,
            relays: Set<String>,
            privateKey: ByteArray,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            var content = message
            var privkey = privateKey
            var pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("p", userHex),
                listOf("relays") + relays
            )
            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                privkey = Utils.privkeyCreate()
                pubKey = Utils.pubkeyCreate(privkey).toHexKey()
                tags = tags + listOf(listOf("anon", ""))
            } else if (zapType == LnZapEvent.ZapType.PRIVATE) {
                var encryptionPrivateKey = createEncryptionPrivateKey(privateKey.toHexKey(), userHex, createdAt)
                var noteJson = (create(privkey, 9733, listOf(tags[0], tags[1]), message)).toJson()
                var encryptedContent = encryptPrivateZapMessage(noteJson, encryptionPrivateKey, userHex.hexToByteArray())
                tags = tags + listOf(listOf("anon", encryptedContent))
                content = ""
                privkey = encryptionPrivateKey
                pubKey = Utils.pubkeyCreate(encryptionPrivateKey).toHexKey()
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun createEncryptionPrivateKey(privkey: String, id: String, createdAt: Long): ByteArray {
            var str = privkey + id + createdAt.toString()
            var strbyte = str.toByteArray(Charset.forName("utf-8"))
            return MessageDigest.getInstance("SHA-256").digest(strbyte)
        }

        private fun encryptPrivateZapMessage(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
            var sharedSecret = Utils.getSharedSecret(privkey, pubkey)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)

            val keySpec = SecretKeySpec(sharedSecret, "AES")
            val ivSpec = IvParameterSpec(iv)

            var utf8message = msg.toByteArray(Charset.forName("utf-8"))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedMsg = cipher.doFinal(utf8message)

            val encryptedMsgBech32 = Bech32.encode("pzap", Bech32.eight2five(encryptedMsg), Bech32.Encoding.Bech32)
            val ivBech32 = Bech32.encode("iv", Bech32.eight2five(iv), Bech32.Encoding.Bech32)

            return encryptedMsgBech32 + "_" + ivBech32
        }

        private fun decryptPrivateZapMessage(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
            var sharedSecret = Utils.getSharedSecret(privkey, pubkey)
            if (sharedSecret.size != 16 && sharedSecret.size != 32) {
                throw IllegalArgumentException("Invalid shared secret size")
            }
            val parts = msg.split("_")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid message format")
            }
            val iv = parts[1].run { Bech32.decode(this).second }
            val encryptedMsg = parts.first().run { Bech32.decode(this).second }
            val encryptedBytes = Bech32.five2eight(encryptedMsg, 0)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(Bech32.five2eight(iv, 0)))

            try {
                val decryptedMsgBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedMsgBytes)
            } catch (ex: BadPaddingException) {
                throw IllegalArgumentException("Bad padding: ${ex.message}")
            }
        }
    }
}
