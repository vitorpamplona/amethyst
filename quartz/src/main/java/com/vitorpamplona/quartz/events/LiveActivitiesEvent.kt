package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class LiveActivitiesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)
    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun streaming() = tags.firstOrNull { it.size > 1 && it[0] == "streaming" }?.get(1)
    fun starts() = tags.firstOrNull { it.size > 1 && it[0] == "starts" }?.get(1)?.toLongOrNull()
    fun ends() = tags.firstOrNull { it.size > 1 && it[0] == "ends" }?.get(1)
    fun status() = checkStatus(tags.firstOrNull { it.size > 1 && it[0] == "status" }?.get(1))
    fun currentParticipants() = tags.firstOrNull { it.size > 1 && it[0] == "current_participants" }?.get(1)
    fun totalParticipants() = tags.firstOrNull { it.size > 1 && it[0] == "total_participants" }?.get(1)

    fun participants() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(3)) }

    fun checkStatus(eventStatus: String?): String? {
        return if (eventStatus == STATUS_LIVE && createdAt < TimeUtils.eightHoursAgo()) {
            STATUS_ENDED
        } else {
            eventStatus
        }
    }

    fun participantsIntersect(keySet: Set<String>): Boolean {
        return tags.any { it.size > 1 && it[0] == "p" && it[1] in keySet }
    }

    companion object {
        const val kind = 30311

        const val STATUS_LIVE = "live"
        const val STATUS_PLANNED = "planned"
        const val STATUS_ENDED = "ended"

        fun create(
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): LiveActivitiesEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return LiveActivitiesEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
