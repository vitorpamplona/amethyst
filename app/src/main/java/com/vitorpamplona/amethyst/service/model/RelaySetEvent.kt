package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class RelaySetEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {

    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun relays() = tags.filter { it.size > 1 && it[0] == "r" }.map { it[1] }
    fun description() = tags.firstOrNull() { it.size > 1 && it[0] == "description" }?.get(1)

    companion object {
        const val kind = 30022

        fun create(
            relays: List<String>,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): RelaySetEvent {
            val tags = mutableListOf<List<String>>()
            relays.forEach {
                tags.add(listOf("r", it))
            }

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = Utils.sign(id, privateKey)
            return RelaySetEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
