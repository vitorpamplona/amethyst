package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class AppRecommendationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun recommendations() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        ATag.parse(it[1], it.getOrNull(2))
    }

    companion object {
        const val kind = 31989
    }
}
