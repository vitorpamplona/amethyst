package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.UserMetadata
import java.io.ByteArrayInputStream

@Immutable
class AppDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun appMetaData() = try {
        MetadataEvent.metadataParser.readValue(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            UserMetadata::class.java
        )
    } catch (e: Exception) {
        e.printStackTrace()
        Log.w("MT", "Content Parse Error ${e.localizedMessage} $content")
        null
    }

    fun supportedKinds() = tags.filter { it.size > 1 && it[0] == "k" }.mapNotNull {
        runCatching { it[1].toInt() }.getOrNull()
    }

    fun publishedAt() = tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)

    companion object {
        const val kind = 31990
    }
}
