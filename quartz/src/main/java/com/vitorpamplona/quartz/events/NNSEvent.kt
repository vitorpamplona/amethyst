package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class NNSEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun ip4() = tags.firstOrNull { it.size > 1 && it[0] == "ip4" }?.get(1)
    fun ip6() = tags.firstOrNull { it.size > 1 && it[0] == "ip6" }?.get(1)
    fun version() = tags.firstOrNull { it.size > 1 && it[0] == "version" }?.get(1)

    companion object {
        const val kind = 30053

        fun create(
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): NNSEvent {
            val tags = mutableListOf<List<String>>()
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return NNSEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
