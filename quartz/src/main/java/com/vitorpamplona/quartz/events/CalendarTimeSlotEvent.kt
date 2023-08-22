package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class CalendarTimeSlotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun location() = tags.firstOrNull { it.size > 1 && it[0] == "location" }?.get(1)
    fun start() = tags.firstOrNull { it.size > 1 && it[0] == "start" }?.get(1)?.toLongOrNull()
    fun end() = tags.firstOrNull { it.size > 1 && it[0] == "end" }?.get(1)?.toLongOrNull()

    fun startTmz() = tags.firstOrNull { it.size > 1 && it[0] == "start_tzid" }?.get(1)?.toLongOrNull()
    fun endTmz() = tags.firstOrNull { it.size > 1 && it[0] == "end_tzid" }?.get(1)?.toLongOrNull()

    //    ["start", "<Unix timestamp in seconds>"],
    //    ["end", "<Unix timestamp in seconds>"],
    //    ["start_tzid", "<IANA Time Zone Database identifier>"],
    //    ["end_tzid", "<IANA Time Zone Database identifier>"],

    companion object {
        const val kind = 31923

        fun create(
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): CalendarTimeSlotEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return CalendarTimeSlotEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
