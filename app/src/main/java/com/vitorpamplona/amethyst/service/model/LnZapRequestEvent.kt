package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.*
import nostr.postr.Bech32
import nostr.postr.Utils
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LnZapRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun zappedPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun zappedAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 9734

        fun create(
            originalNote: EventInterface,
            relays: Set<String>,
            privateKey: ByteArray,
            pollOption: Int?,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = Date().time / 1000
        ): LnZapRequestEvent {
            var content = message
            var privkey = privateKey
            var pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", originalNote.pubKey()),
                listOf("relays") + relays
            )
            if (originalNote is LongTextNoteEvent) {
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
                var enc_prkey = create_private_key(privateKey, originalNote.id(), createdAt)
                var noteJson = (create(privkey, 9733, listOf(tags[0], tags[1]), message)).toJson()
                var privreq = encrypt_privatezap_message(noteJson, enc_prkey, originalNote.pubKey().toByteArray())
                tags = tags + listOf(listOf("anon", privreq))
                content = ""
                privkey = enc_prkey
                pubKey = Utils.pubkeyCreate(enc_prkey).toHexKey()
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create_private_key(privkey: ByteArray, id: String, createdAt: Long): ByteArray {
            var str = privkey.toHexKey() + id + createdAt.toString()
            var strbyte = str.toByteArray(Charset.forName("utf-8"))
            return sha256.digest(strbyte)
        }

        fun encrypt_privatezap_message(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
            var sharedSecret = Utils.getSharedSecret(privkey, pubkey)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)

            val keySpec = SecretKeySpec(sharedSecret, "AES")
            val ivSpec = IvParameterSpec(iv)

            var utf8_message = msg.toByteArray(Charset.forName("utf-8"))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedMsg = cipher.doFinal(utf8_message)

            val encryptedMsgBech32 = Bech32.encode("pzap", Bech32.eight2five(encryptedMsg), Bech32.Encoding.Bech32)
            val ivBech32 = Bech32.encode("iv", Bech32.eight2five(iv), Bech32.Encoding.Bech32)

            return encryptedMsgBech32 + "_" + ivBech32
        }

        fun decrypt_privatezap_message(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
            var sharedSecret = Utils.getSharedSecret(privkey, pubkey)
            val parts = msg.split("_")
            val iv = parts[1].run { Bech32.decode(this) }
            val encryptedMsg = parts.first().run { Bech32.decode(this) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(Bech32.five2eight(iv.second, 0)))
            return String(cipher.doFinal(Bech32.five2eight(encryptedMsg.second, 0)))
        }

        fun test_decrypt(privateKey: ByteArray, event: Event): String {
            val anonTag = event.tags.firstOrNull { t -> t.count() >= 2 && t[0] == "anon" }
            var encnote = anonTag?.elementAt(1)
            if (encnote != null) {
                var enc_prkey2 = create_private_key(privateKey, event.id(), event.createdAt)
                return decrypt_privatezap_message(encnote, enc_prkey2, event.pubKey().toByteArray())
            } else {
                return ""
            }
        }
        fun create(
            userHex: String,
            relays: Set<String>,
            privateKey: ByteArray,
            message: String,
            zapType: LnZapEvent.ZapType,
            createdAt: Long = Date().time / 1000
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
                var enc_prkey = create_private_key(privateKey, userHex, createdAt)
                var noteJson = (create(privkey, 9733, listOf(tags[0], tags[1]), message)).toJson()
                var privreq = encrypt_privatezap_message(noteJson, enc_prkey, userHex.toByteArray())
                tags = tags + listOf(listOf("anon", privreq))
                content = ""
                privkey = enc_prkey
                pubKey = Utils.pubkeyCreate(enc_prkey).toHexKey()
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privkey)
            return LnZapRequestEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
/*
{
  "pubkey": "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245",
  "content": "",
  "id": "d9cc14d50fcb8c27539aacf776882942c1a11ea4472f8cdec1dea82fab66279d",
  "created_at": 1674164539,
  "sig": "77127f636577e9029276be060332ea565deaf89ff215a494ccff16ae3f757065e2bc59b2e8c113dd407917a010b3abd36c8d7ad84c0e3ab7dab3a0b0caa9835d",
  "kind": 9734,
  "tags": [
  [
    "e",
    "3624762a1274dd9636e0c552b53086d70bc88c165bc4dc0f9e836a1eaf86c3b8"
  ],
  [
    "p",
    "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"
  ],
  [
    "relays",
    "wss://relay.damus.io",
    "wss://nostr-relay.wlvs.space",
    "wss://nostr.fmt.wiz.biz",
    "wss://relay.nostr.bg",
    "wss://nostr.oxtr.dev",
    "wss://nostr.v0l.io",
    "wss://brb.io",
    "wss://nostr.bitcoiner.social",
    "ws://monad.jb55.com:8080",
    "wss://relay.snort.social"
  ],
  [
    "poll_option", "n"
  ]
  ],
  "ots": <base64-encoded OTS file data> // TODO
}
*/
