/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip89AppHandlers.definition

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.isTaggedKind
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kinds
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip89AppHandlers.PlatformType
import com.vitorpamplona.quartz.nip89AppHandlers.definition.tags.PlatformLinkTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
@OptIn(ExperimentalUuidApi::class)
class AppDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PublishedAtProvider {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var cachedMetadata: AppMetadata? = null

    fun appMetaData() =
        if (cachedMetadata != null) {
            cachedMetadata
        } else {
            try {
                if (content.startsWith("{")) {
                    val newMetadata = AppMetadata.parse(content)
                    cachedMetadata = newMetadata
                    newMetadata
                } else {
                    val newMetadata = AppMetadata()
                    newMetadata.name = content
                    cachedMetadata = newMetadata
                    newMetadata
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("AppDefinitionEvent", "Content Parse Error: ${toNostrUri()} ${e.message}", e)
                null
            }
        }

    fun supportedKinds() = tags.kinds()

    fun includeKind(kind: Int) = tags.isTaggedKind(kind)

    override fun publishedAt(): Long? {
        val publishedAt = tags.firstNotNullOfOrNull(PublishedAtTag::parse)

        if (publishedAt == null) return null

        // removes posts in the future.
        return if (publishedAt <= createdAt) {
            publishedAt
        } else {
            null
        }
    }

    companion object {
        const val KIND = 31990
        const val ALT_DESCRIPTION = "App definition event"

        // ["web", "https://..../a/<bech32>", "nevent"]
        class PlatformLink(
            val platform: PlatformType,
            val uri: String,
            val entityType: EntityType?,
        ) {
            fun toPlatformLinkTag() = PlatformLinkTag(platform.code, uri, entityType?.code)

            fun toTagArray() = toPlatformLinkTag().toTagArray()
        }

        fun build(
            details: AppMetadata,
            supportedKinds: Set<Int>,
            links: List<PlatformLink>,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AppDefinitionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, details.toJson(), createdAt) {
            dTag(dTag)
            alt(ALT_DESCRIPTION)
            kinds(supportedKinds)
            links(links)
            initializer()
        }
    }
}
