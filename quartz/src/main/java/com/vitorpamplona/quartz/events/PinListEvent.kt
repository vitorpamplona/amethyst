package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class PinListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun pins() = tags.filter { it.size > 1 && it[0] == "pin" }.map { it[1] }

    companion object {
        const val kind = 33888

        fun create(
            pins: List<String>,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): PinListEvent {
            val tags = mutableListOf<List<String>>()
            pins.forEach {
                tags.add(listOf("pin", it))
            }

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return PinListEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
