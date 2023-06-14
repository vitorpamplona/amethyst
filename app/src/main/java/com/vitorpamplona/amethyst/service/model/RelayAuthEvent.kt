package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.Nip26
import nostr.postr.Utils
import java.util.Date

@Immutable
class RelayAuthEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun relay() = tags.firstOrNull() { it.size > 1 && it[0] == "relay" }?.get(1)
    fun challenge() = tags.firstOrNull() { it.size > 1 && it[0] == "challenge" }?.get(1)

    companion object {
        const val kind = 22242

        fun create(
            relay: String,
            challenge: String,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000,
            delegationToken: String,
            delegationHexKey: String,
            delegationSignature: String
        ): RelayAuthEvent {
            val content = ""
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = listOf(
                listOf("relay", relay),
                listOf("challenge", challenge)
            )
            if (delegationToken.isNotBlank()) {
                tags = tags + listOf(Nip26.toTags(delegationToken, delegationSignature, delegationHexKey))
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RelayAuthEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
