package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class EmojiPackSelectionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    companion object {
        const val kind = 10030

        fun create(
            listOfEmojiPacks: List<ATag>?,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): EmojiPackSelectionEvent {
            val msg = ""
            val pubKey = keyPair.pubKey.toHexKey()
            val tags = mutableListOf<List<String>>()

            listOfEmojiPacks?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return EmojiPackSelectionEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig?.toHexKey() ?: "")
        }

        fun create(
            unsignedEvent: EmojiPackSelectionEvent, signature: String
        ): EmojiPackSelectionEvent {
            return EmojiPackSelectionEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
