package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class EmojiPackSelectionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    override fun dTag() = fixedDTag

    companion object {
        const val kind = 10030
        const val fixedDTag = ""

        fun create(
            listOfEmojiPacks: List<ATag>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EmojiPackSelectionEvent) -> Unit
        ) {
            val msg = ""
            val tags = mutableListOf<List<String>>()

            listOfEmojiPacks?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            signer.sign(createdAt, kind, tags, msg, onReady)
        }
    }
}
