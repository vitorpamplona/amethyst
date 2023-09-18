package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.*
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import java.nio.charset.Charset
import java.security.SecureRandom
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
            toUserPubHex: String?, // Overrides in case of Zap Splits
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            var content = message
            var privkey = privateKey
            var pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", toUserPubHex ?: originalNote.pubKey()),
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
                privkey = CryptoUtils.privkeyCreate()
                pubKey = CryptoUtils.pubkeyCreate(privkey).toHexKey()
            } else if (zapType == LnZapEvent.ZapType.PRIVATE) {
                val encryptionPrivateKey = createEncryptionPrivateKey(privateKey.toHexKey(), originalNote.id(), createdAt)
                val noteJson = (LnZapPrivateEvent.create(privkey, listOf(tags[0], tags[1]), message)).toJson()
                val encryptedContent = encryptPrivateZapMessage(noteJson, encryptionPrivateKey, originalNote.pubKey().hexToByteArray())
                tags = tags + listOf(listOf("anon", encryptedContent))
                content = "" // make sure public content is empty, as the content is encrypted
                privkey = encryptionPrivateKey // sign event with generated privkey
                pubKey = CryptoUtils.pubkeyCreate(encryptionPrivateKey).toHexKey() // updated event with according pubkey
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create(
            unsignedEvent: LnZapRequestEvent,
            signature: String
        ): LnZapRequestEvent {
            return LnZapRequestEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }

        fun createPublic(
            userHex: String,
            relays: Set<String>,
            pubKey: HexKey,
            message: String,
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            val tags = listOf(
                listOf("p", userHex),
                listOf("relays") + relays
            )

            val id = generateId(pubKey, createdAt, kind, tags, message)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, message, "")
        }

        fun createPublic(
            originalNote: EventInterface,
            relays: Set<String>,
            pubKey: HexKey,
            pollOption: Int?,
            message: String,
            toUserPubHex: String?, // Overrides in case of Zap Splits
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", toUserPubHex ?: originalNote.pubKey()),
                listOf("relays") + relays
            )
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(listOf(POLL_OPTION, pollOption.toString()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, message)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, message, "")
        }

        fun createPrivateZap(
            originalNote: EventInterface,
            relays: Set<String>,
            pubKey: HexKey,
            pollOption: Int?,
            message: String,
            toUserPubHex: String?,
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            val content = message
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", toUserPubHex ?: originalNote.pubKey()),
                listOf("relays") + relays
            )
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(listOf(POLL_OPTION, pollOption.toString()))
            }

            tags = tags + listOf(listOf("anon", ""))

            return LnZapRequestEvent("zap", pubKey, createdAt, tags, content, "")
        }

        fun createPrivateZap(
            userHex: String,
            relays: Set<String>,
            pubKey: HexKey,
            message: String,
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            val content = message
            var tags = listOf(
                listOf("p", userHex),
                listOf("relays") + relays
            )
            tags = tags + listOf(listOf("anon", ""))

            return LnZapRequestEvent("zap", pubKey, createdAt, tags, content, "")
        }

        fun createAnonymous(
            originalNote: EventInterface,
            relays: Set<String>,
            pollOption: Int?,
            message: String,
            toUserPubHex: String?, // Overrides in case of Zap Splits
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", toUserPubHex ?: originalNote.pubKey()),
                listOf("relays") + relays
            )
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }
            if (pollOption != null && pollOption >= 0) {
                tags = tags + listOf(listOf(POLL_OPTION, pollOption.toString()))
            }

            tags = tags + listOf(listOf("anon", ""))
            val privkey = CryptoUtils.privkeyCreate()
            val pubKey = CryptoUtils.pubkeyCreate(privkey).toHexKey()

            val id = generateId(pubKey, createdAt, kind, tags, message)
            val sig = CryptoUtils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, message, sig.toHexKey())
        }

        fun createAnonymous(
            userHex: String,
            relays: Set<String>,
            message: String,
            createdAt: Long = TimeUtils.now()
        ): LnZapRequestEvent {
            var tags = listOf(
                listOf("p", userHex),
                listOf("relays") + relays
            )

            tags = tags + listOf(listOf("anon", ""))
            val privkey = CryptoUtils.privkeyCreate()
            val pubKey = CryptoUtils.pubkeyCreate(privkey).toHexKey()

            val id = generateId(pubKey, createdAt, kind, tags, message)
            val sig = CryptoUtils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, message, sig.toHexKey())
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
            var pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("p", userHex),
                listOf("relays") + relays
            )
            if (zapType == LnZapEvent.ZapType.ANONYMOUS) {
                privkey = CryptoUtils.privkeyCreate()
                pubKey = CryptoUtils.pubkeyCreate(privkey).toHexKey()
                tags = tags + listOf(listOf("anon", ""))
            } else if (zapType == LnZapEvent.ZapType.PRIVATE) {
                val encryptionPrivateKey = createEncryptionPrivateKey(privateKey.toHexKey(), userHex, createdAt)
                val noteJson = LnZapPrivateEvent.create(privkey, listOf(tags[0], tags[1]), message).toJson()
                val encryptedContent = encryptPrivateZapMessage(noteJson, encryptionPrivateKey, userHex.hexToByteArray())
                tags = tags + listOf(listOf("anon", encryptedContent))
                content = ""
                privkey = encryptionPrivateKey
                pubKey = CryptoUtils.pubkeyCreate(encryptionPrivateKey).toHexKey()
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }


        fun createEncryptionPrivateKey(privkey: String, id: String, createdAt: Long): ByteArray {
            val str = privkey + id + createdAt.toString()
            val strbyte = str.toByteArray(Charset.forName("utf-8"))
            return CryptoUtils.sha256(strbyte)
        }

        private fun encryptPrivateZapMessage(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privkey, pubkey)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)

            val keySpec = SecretKeySpec(sharedSecret, "AES")
            val ivSpec = IvParameterSpec(iv)

            val utf8message = msg.toByteArray(Charset.forName("utf-8"))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedMsg = cipher.doFinal(utf8message)

            val encryptedMsgBech32 = Bech32.encode("pzap", Bech32.eight2five(encryptedMsg), Bech32.Encoding.Bech32)
            val ivBech32 = Bech32.encode("iv", Bech32.eight2five(iv), Bech32.Encoding.Bech32)

            return encryptedMsgBech32 + "_" + ivBech32
        }

        private fun decryptPrivateZapMessage(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privkey, pubkey)
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
            cipher.init(
                Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(
                    Bech32.five2eight(iv, 0))
            )

            try {
                val decryptedMsgBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedMsgBytes)
            } catch (ex: BadPaddingException) {
                throw IllegalArgumentException("Bad padding: ${ex.message}")
            }
        }
    }
}
