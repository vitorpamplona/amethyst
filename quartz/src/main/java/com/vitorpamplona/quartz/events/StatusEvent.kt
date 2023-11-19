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
class StatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 30315

        fun create(
            msg: String,
            type: String,
            expiration: Long?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (StatusEvent) -> Unit
        ) {
            val tags = mutableListOf<List<String>>()

            tags.add(listOf("d", type))
            expiration?.let { tags.add(listOf("expiration", it.toString())) }

            signer.sign(createdAt, kind, tags, msg, onReady)
        }

        fun update(
            event: StatusEvent,
            newStatus: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (StatusEvent) -> Unit
        ) {
            val tags = event.tags
            signer.sign(createdAt, kind, tags, newStatus, onReady)
        }

        fun clear(
            event: StatusEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (StatusEvent) -> Unit
        ) {
            val msg = ""
            val tags = event.tags.filter { it.size > 1 && it[0] == "d" }
            signer.sign(createdAt, kind, tags, msg, onReady)
        }
    }
}
