package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class BadgeAwardEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun awardees() = taggedUsers()

    fun awardDefinition() = taggedAddresses()

    companion object {
        const val kind = 8
    }
}
