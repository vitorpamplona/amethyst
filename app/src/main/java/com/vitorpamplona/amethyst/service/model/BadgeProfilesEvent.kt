package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey

class BadgeProfilesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun badgeAwardEvents() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun badgeAwardDefinitions() = tags.filter { it.firstOrNull() == "a" }.mapNotNull { it.getOrNull(1) }.mapNotNull { ATag.parse(it) }

    fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""
    fun address() = ATag(kind, pubKey, dTag())

    companion object {
        const val kind = 30008
    }
}
