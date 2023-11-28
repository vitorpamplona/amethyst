package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class AdvertisedRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AdvertisedRelayListEvent) -> Unit
        ) {
            val tags = list.map {
                if (it.type == AdvertisedRelayType.BOTH) {
                    arrayOf(it.relayUrl)
                } else {
                    arrayOf(it.relayUrl, it.type.code)
                }
            }.toTypedArray()
            val msg = ""

            signer.sign(createdAt, kind, tags, msg, onReady)
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
