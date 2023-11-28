package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class CalendarDateSlotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CalendarDateSlotEvent) -> Unit
        ) {
            val tags = emptyArray<Array<String>>()
            signer.sign(createdAt, kind, tags, "", onReady)
        }
    }
}
