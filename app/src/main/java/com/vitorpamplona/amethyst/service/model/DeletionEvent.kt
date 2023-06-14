package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.Nip26
import nostr.postr.Utils
import java.util.Date

@Immutable
class DeletionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun deleteEvents() = tags.map { it[1] }

    companion object {
        const val kind = 5

        fun create(
            deleteEvents: List<String>,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000,
            delegationToken: String,
            delegationHexKey: String,
            delegationSignature: String
        ): DeletionEvent {
            val content = ""
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags = deleteEvents.map { listOf("e", it) }
            if (delegationToken.isNotBlank()) {
                tags = tags + listOf(Nip26.toTags(delegationToken, delegationSignature, delegationHexKey))
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return DeletionEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
