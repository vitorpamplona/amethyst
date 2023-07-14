package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils

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
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()

            listOfEmojiPacks?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return EmojiPackSelectionEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}
