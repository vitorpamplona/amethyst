package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class NNSEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun ip4() = tags.firstOrNull { it.size > 1 && it[0] == "ip4" }?.get(1)
    fun ip6() = tags.firstOrNull { it.size > 1 && it[0] == "ip6" }?.get(1)
    fun version() = tags.firstOrNull { it.size > 1 && it[0] == "version" }?.get(1)

    companion object {
        const val kind = 30053

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (NNSEvent) -> Unit
        ) {
            val tags = emptyArray<Array<String>>()
            signer.sign(createdAt, kind, tags, "", onReady)
        }
    }
}
