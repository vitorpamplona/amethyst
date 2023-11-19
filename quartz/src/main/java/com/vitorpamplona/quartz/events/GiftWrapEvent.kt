package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerInternal

@Immutable
class GiftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    fun cachedGift(signer: NostrSigner, onReady: (Event) -> Unit) {
        cachedInnerEvent[signer.pubKey]?.let {
            onReady(it)
            return
        }

        unwrap(signer) { gift ->
            if (gift is WrappedEvent) {
                gift.host = this
            }
            cachedInnerEvent = cachedInnerEvent + Pair(signer.pubKey, gift)

            onReady(gift)
        }
    }

    private fun unwrap(signer: NostrSigner, onReady: (Event) -> Unit) {
        try {
            plainContent(signer) {
                onReady(fromJson(it))
            }
        } catch (e: Exception) {
            // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
        }
    }

    private fun plainContent(signer: NostrSigner, onReady: (String) -> Unit) {
        if (content.isEmpty()) return

        signer.nip44Decrypt(content, pubKey, onReady)
    }

    fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    companion object {
        const val kind = 1059

        fun create(
            event: Event,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.randomWithinAWeek(),
            onReady: (GiftWrapEvent) -> Unit
        ) {
            val signer = NostrSignerInternal(KeyPair()) // GiftWrap is always a random key
            val serializedContent = toJson(event)
            val tags = listOf(listOf("p", recipientPubKey))

            signer.nip44Encrypt(serializedContent, recipientPubKey) {
                signer.sign(createdAt, kind, tags, it, onReady)
            }
        }
    }
}
