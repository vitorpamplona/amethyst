package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey

@Immutable
class BadgeDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun name() = tags.firstOrNull { it.size > 1 && it[0] == "name" }?.get(1)
    fun thumb() = tags.firstOrNull { it.size > 1 && it[0] == "thumb" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun description() = tags.firstOrNull { it.size > 1 && it[0] == "description" }?.get(1)

    companion object {
        const val kind = 30009
    }
}
