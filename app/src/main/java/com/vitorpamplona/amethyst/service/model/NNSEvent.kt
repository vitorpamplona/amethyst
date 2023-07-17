package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils

@Immutable
class NNSEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {

    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

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
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = Utils.sign(id, privateKey)
            return NNSEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}
