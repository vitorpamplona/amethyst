package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import java.util.UUID

@Immutable
class MuteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var privateTagsCache: List<List<String>>? = null

    private fun privateTags(signer: NostrSigner, onReady: (List<List<String>>) -> Unit) {
        if (content.isBlank()) return

        privateTagsCache?.let {
            onReady(it)
            return
        }

        try {
            signer.nip04Decrypt(content, pubKey) {
                privateTagsCache = mapper.readValue<List<List<String>>>(it)
                privateTagsCache?.let {
                    onReady(it)
                }
            }
        } catch (e: Throwable) {
            Log.w("MuteList", "Error parsing the JSON ${e.message}")
        }
    }

    fun privateTaggedUsers(signer: NostrSigner, onReady: (List<String>) -> Unit) = privateTags(signer) {
        onReady(it.filter { it.size > 1 && it[0] == "p" }.map { it[1] } )
    }
    fun privateHashtags(signer: NostrSigner, onReady: (List<String>) -> Unit) = privateTags(signer) {
        onReady(it.filter { it.size > 1 && it[0] == "t" }.map { it[1] } )
    }
    fun privateGeohashes(signer: NostrSigner, onReady: (List<String>) -> Unit) = privateTags(signer) {
        onReady(it.filter { it.size > 1 && it[0] == "g" }.map { it[1] } )
    }
    fun privateTaggedEvents(signer: NostrSigner, onReady: (List<String>) -> Unit) = privateTags(signer) {
        onReady(it.filter { it.size > 1 && it[0] == "e" }.map { it[1] } )
    }

    fun privateTaggedAddresses(signer: NostrSigner, onReady: (List<ATag>) -> Unit) = privateTags(signer) {
        onReady(
            it.filter { it.firstOrNull() == "a" }.mapNotNull {
                val aTagValue = it.getOrNull(1)
                val relay = it.getOrNull(2)

                if (aTagValue != null) ATag.parse(aTagValue, relay) else null
            }
        )
    }

    companion object {
        const val kind = 10000

        fun create(
            events: List<String>? = null,
            users: List<String>? = null,
            addresses: List<ATag>? = null,

            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,

            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (MuteListEvent) -> Unit
        ) {
            val privTags = mutableListOf<List<String>>()
            privEvents?.forEach {
                privTags.add(listOf("e", it))
            }
            privUsers?.forEach {
                privTags.add(listOf("p", it))
            }
            privAddresses?.forEach {
                privTags.add(listOf("a", it.toTag()))
            }
            val msg = mapper.writeValueAsString(privTags)

            val tags = mutableListOf<List<String>>()
            events?.forEach {
                tags.add(listOf("e", it))
            }
            users?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            signer.nip04Encrypt(msg, signer.pubKey) { content ->
                signer.sign(createdAt, kind, tags, content, onReady)
            }
        }
    }
}
