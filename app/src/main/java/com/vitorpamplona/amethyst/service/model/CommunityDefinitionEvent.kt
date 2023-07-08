package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class CommunityDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {

    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun description() = tags.firstOrNull { it.size > 1 && it[0] == "description" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun rules() = tags.firstOrNull { it.size > 1 && it[0] == "rules" }?.get(1)

    fun moderators() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(3)) }

    companion object {
        const val kind = 34550

        fun create(
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): CommunityDefinitionEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = Utils.sign(id, privateKey)
            return CommunityDefinitionEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
