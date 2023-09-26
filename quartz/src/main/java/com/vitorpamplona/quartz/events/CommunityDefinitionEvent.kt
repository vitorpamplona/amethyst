package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class CommunityDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
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
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): CommunityDefinitionEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return CommunityDefinitionEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
