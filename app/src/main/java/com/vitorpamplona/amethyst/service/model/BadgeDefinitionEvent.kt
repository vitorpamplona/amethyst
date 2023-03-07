package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import java.util.Date
import nostr.postr.Utils

class BadgeDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""
    fun address() = ATag(kind, pubKey, dTag(), null)

    fun name() = tags.filter { it.firstOrNull() == "name" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun thumb() = tags.filter { it.firstOrNull() == "thumb" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun image() = tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun description() = tags.filter { it.firstOrNull() == "description" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    companion object {
        const val kind = 30009
    }
}
