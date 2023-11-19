package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayInputStream

@Immutable
class AppDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var cachedMetadata: UserMetadata? = null

    fun appMetaData() =
        if (cachedMetadata != null) {
            cachedMetadata
        } else {
            try {
                val newMetadata = mapper.readValue(
                    ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
                    UserMetadata::class.java
                )

                cachedMetadata = newMetadata

                newMetadata
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w("MT", "Content Parse Error ${e.localizedMessage} $content")
                null
            }
        }

    fun supportedKinds() = tags.filter { it.size > 1 && it[0] == "k" }.mapNotNull {
        runCatching { it[1].toInt() }.getOrNull()
    }

    fun publishedAt() = tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)

    companion object {
        const val kind = 31990

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AppDefinitionEvent) -> Unit
        ) {
            val tags = mutableListOf<List<String>>()
            signer.sign(createdAt, kind, tags, "", onReady)
        }
    }
}
