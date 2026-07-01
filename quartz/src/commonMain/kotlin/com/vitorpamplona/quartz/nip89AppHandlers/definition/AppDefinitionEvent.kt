/*
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
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTags
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedATags
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.kinds.isTaggedKind
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kinds
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip89AppHandlers.PlatformType
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.client
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
    PublishedAtProvider,
    SearchableEvent {
    // App-handler content is JSON; parse it and index the human-meaningful
    // fields plus the addresses/URLs people search by.
    override fun indexableContent() =
        appMetaData()?.let {
            listOfNotNull(
                it.name,
                it.username,
                it.displayName,
                it.about,
                it.nip05,
                it.lud06,
                it.lud16,
                it.website,
                it.picture,
                it.banner,
                it.image,
            ).joinToString(" ")
        } ?: ""

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

    fun platformLinks() = tags.platformLinks()

    /** Categories the app declares via `t` tags (e.g. "social", "video"). */
    fun categories() = tags.hashtags()

    /** NIPs the app declares it supports via NostrHub-style `i` tags. */
    fun supportedNips() = tags.supportedNips()

    /** Related addressable events referenced via `a` tags (source repo, store listing, ...). */
    fun relatedAddresses() = tags.taggedATags()

    /** The client (NIP-89 `client` tag) that published this handler, if any. */
    fun client() = tags.client().firstOrNull()

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
            categories: List<String> = emptyList(),
            supportedNips: List<String> = emptyList(),
            relatedAddresses: List<ATag> = emptyList(),
            client: String? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AppDefinitionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, details.toJson(), createdAt) {
            dTag(dTag)
            kinds(supportedKinds)
            links(links)
            if (categories.isNotEmpty()) hashtags(categories)
            if (supportedNips.isNotEmpty()) supportedNips(supportedNips)
            if (relatedAddresses.isNotEmpty()) aTags(relatedAddresses)
            client?.let { client(it) }
            initializer()
        }
    }
}
