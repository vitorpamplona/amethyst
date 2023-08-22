package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class AdvertisedRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    override fun dTag() = fixedDTag

    fun relays(): List<AdvertisedRelayInfo> {
        return tags.mapNotNull {
            if (it.size > 1 && it[0] == "r") {
                val type = when (it.getOrNull(2)) {
                    "read" -> AdvertisedRelayType.READ
                    "write" -> AdvertisedRelayType.WRITE
                    else -> AdvertisedRelayType.BOTH
                }

                AdvertisedRelayInfo(it[1], type)
            } else {
                null
            }
        }
    }

    companion object {
        const val kind = 10002
        const val fixedDTag = ""

        fun create(
            list: List<AdvertisedRelayInfo>,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): AdvertisedRelayListEvent {
            val tags = list.map {
                if (it.type == AdvertisedRelayType.BOTH) {
                    listOf(it.relayUrl)
                } else {
                    listOf(it.relayUrl, it.type.code)
                }
            }
            val msg = ""
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = CryptoUtils.sign(id, privateKey)
            return AdvertisedRelayListEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }

    @Immutable
    data class AdvertisedRelayInfo(val relayUrl: String, val type: AdvertisedRelayType)

    @Immutable
    enum class AdvertisedRelayType(val code: String) {
        BOTH(""),
        READ("read"),
        WRITE("write")
    }
}
