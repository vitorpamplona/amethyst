package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class HTTPAuthorizationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 27235

        fun create(
            url: String,
            method: String,
            body: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HTTPAuthorizationEvent) -> Unit
        ) {
            var hash = ""
            body?.let {
                hash = CryptoUtils.sha256(it.toByteArray()).toHexKey()
            }

            val tags = listOfNotNull(
                arrayOf("u", url),
                arrayOf("method", method),
                arrayOf("payload", hash)
            )

            signer.sign(createdAt, kind, tags.toTypedArray(), "", onReady)
        }
    }
}
