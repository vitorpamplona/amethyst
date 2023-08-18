package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
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
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {

    override fun dTag() = ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    companion object {
        const val kind = 10030

        fun create(
            listOfEmojiPacks: List<ATag>?,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): EmojiPackSelectionEvent {
            val msg = ""
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()

            listOfEmojiPacks?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = CryptoUtils.sign(id, privateKey)
            return EmojiPackSelectionEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}
