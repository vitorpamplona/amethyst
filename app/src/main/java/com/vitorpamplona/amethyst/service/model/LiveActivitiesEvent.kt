package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class LiveActivitiesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {

    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)
    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun streaming() = tags.firstOrNull { it.size > 1 && it[0] == "streaming" }?.get(1)
    fun starts() = tags.firstOrNull { it.size > 1 && it[0] == "starts" }?.get(1)
    fun ends() = tags.firstOrNull { it.size > 1 && it[0] == "ends" }?.get(1)
    fun status() = tags.firstOrNull { it.size > 1 && it[0] == "status" }?.get(1)
    fun currentParticipants() = tags.firstOrNull { it.size > 1 && it[0] == "current_participants" }?.get(1)
    fun totalParticipants() = tags.firstOrNull { it.size > 1 && it[0] == "total_participants" }?.get(1)

    fun participants() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(2)) }

    companion object {
        const val kind = 30311

        fun create(
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): LiveActivitiesEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = Utils.sign(id, privateKey)
            return LiveActivitiesEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
