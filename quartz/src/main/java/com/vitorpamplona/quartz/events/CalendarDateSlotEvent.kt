package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class CalendarDateSlotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun location() = tags.firstOrNull { it.size > 1 && it[0] == "location" }?.get(1)
    fun start() = tags.firstOrNull { it.size > 1 && it[0] == "start" }?.get(1)
    fun end() = tags.firstOrNull { it.size > 1 && it[0] == "end" }?.get(1)

    //  ["start", "<YYYY-MM-DD>"],
    //  ["end", "<YYYY-MM-DD>"],

    companion object {
        const val kind = 31922

        fun create(
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): CalendarDateSlotEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return CalendarDateSlotEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
