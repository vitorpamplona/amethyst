package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
data class Contact(val pubKeyHex: String, val relayUri: String?)

class ContactListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    // This function is only used by the user logged in
    // But it is used all the time.
    val verifiedFollowKeySet: Set<HexKey> by lazy {
        tags.filter { it.size > 1 && it[0] == "p" }.mapNotNull {
            try {
                decodePublicKey(it[1]).toHexKey()
            } catch (e: Exception) {
                Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
                null
            }
        }.toSet()
    }

    val verifiedFollowTagSet: Set<String> by lazy {
        unverifiedFollowTagSet().map { it.lowercase() }.toSet()
    }

    val verifiedFollowKeySetAndMe: Set<HexKey> by lazy {
        verifiedFollowKeySet + pubKey
    }

    fun unverifiedFollowKeySet() = tags.filter { it[0] == "p" }.mapNotNull { it.getOrNull(1) }
    fun unverifiedFollowTagSet() = tags.filter { it[0] == "t" }.mapNotNull { it.getOrNull(1) }

    fun follows() = tags.filter { it[0] == "p" }.mapNotNull {
        try {
            Contact(decodePublicKey(it[1]).toHexKey(), it.getOrNull(2))
        } catch (e: Exception) {
            Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
            null
        }
    }

    fun followsTags() = tags.filter { it[0] == "t" }.mapNotNull {
        it.getOrNull(2)
    }

    fun relays(): Map<String, ReadWrite>? = try {
        if (content.isNotEmpty()) {
            gson.fromJson(content, object : TypeToken<Map<String, ReadWrite>>() {}.type) as Map<String, ReadWrite>
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w("ContactListEvent", "Can't parse content as relay lists: $content", e)
        null
    }

    companion object {
        const val kind = 3

        fun create(follows: List<Contact>, followTags: List<String>, relayUse: Map<String, ReadWrite>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ContactListEvent {
            val content = if (relayUse != null) {
                gson.toJson(relayUse)
            } else {
                ""
            }
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = follows.map {
                if (it.relayUri != null) {
                    listOf("p", it.pubKeyHex, it.relayUri)
                } else {
                    listOf("p", it.pubKeyHex)
                }
            } + followTags.map {
                listOf("t", it)
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ContactListEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }

    data class ReadWrite(val read: Boolean, val write: Boolean)
}
