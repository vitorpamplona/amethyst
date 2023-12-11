package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class FileServersEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    override fun dTag() = fixedDTag

    companion object {
        const val kind = 10096
        const val fixedDTag = ""

        fun create(
            listOfServers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileServersEvent) -> Unit
        ) {
            val msg = ""
            val tags = mutableListOf<Array<String>>()

            listOfServers.forEach {
                tags.add(arrayOf("server", it))
            }

            signer.sign(createdAt, kind, tags.toTypedArray(), msg, onReady)
        }
    }
}
