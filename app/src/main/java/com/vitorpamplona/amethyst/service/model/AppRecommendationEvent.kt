package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey

@Immutable
class AppRecommendationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    fun recommendations() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        ATag.parse(it[1], it.getOrNull(2))
    }

    fun forKind() = runCatching { dTag().toInt() }.getOrNull()

    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    companion object {
        const val kind = 31989
    }
}
