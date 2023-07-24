package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.Utils

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
            createdAt: Long = TimeUtils.now()
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
