package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class CommunityDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun description() = tags.firstOrNull { it.size > 1 && it[0] == "description" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun rules() = tags.firstOrNull { it.size > 1 && it[0] == "rules" }?.get(1)

    fun moderators() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(3)) }

    companion object {
        const val kind = 34550

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityDefinitionEvent) -> Unit
        ) {
            val tags = emptyArray<Array<String>>()
            signer.sign(createdAt, kind, tags, "", onReady)
        }
    }
}
