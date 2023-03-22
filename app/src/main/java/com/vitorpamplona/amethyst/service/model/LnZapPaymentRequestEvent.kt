package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

class LnZapPaymentRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun lnInvoice(privKey: ByteArray): String? {
        return try {
            val sharedSecret = Utils.getSharedSecret(privKey, pubKey.toByteArray())

            return Utils.decrypt(content, sharedSecret)
        } catch (e: Exception) {
            Log.w("BookmarkList", "Error decrypting the message ${e.message}")
            null
        }
    }

    companion object {
        const val kind = 23194

        fun create(
            lnInvoice: String,
            walletServicePubkey: String,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): LnZapPaymentRequestEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)

            val content = Utils.encrypt(
                lnInvoice,
                privateKey,
                walletServicePubkey.toByteArray()
            )

            val tags = mutableListOf<List<String>>()
            tags.add(listOf("p", walletServicePubkey))

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return LnZapPaymentRequestEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}
