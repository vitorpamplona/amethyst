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
package com.vitorpamplona.quartz.nip01Core.metadata

import android.util.Log
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.builder
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.metadata.tags.AboutTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.BannerTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.DisplayNameTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Lud06Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Lud16Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.MoneroAddressesTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.NameTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Nip05Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.PictureTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.PronounsTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.WebsiteTag
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.claims
import com.vitorpamplona.quartz.nip39ExtIdentities.githubClaim
import com.vitorpamplona.quartz.nip39ExtIdentities.mastodonClaim
import com.vitorpamplona.quartz.nip39ExtIdentities.replaceClaims
import com.vitorpamplona.quartz.nip39ExtIdentities.twitterClaim
import com.vitorpamplona.quartz.utils.TimeUtils

class MetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun contactMetadataJson() = jacksonObjectMapper().readTree(content) as? ObjectNode

    fun contactMetaData() =
        try {
            EventMapper.mapper.readValue(content, UserMetadata::class.java)
        } catch (e: Exception) {
            Log.w("MetadataEvent", "Content Parse Error: ${toNostrUri()} ${e.localizedMessage}")
            null
        }

    companion object {
        const val KIND = 0

        fun newUser(
            name: String?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MetadataEvent>.() -> Unit = {},
        ): EventTemplate<MetadataEvent> {
            val metadata = JsonNodeFactory.instance.objectNode()
            name?.let { addIfNotBlank(metadata, "name", it.trim()) }
            return eventTemplate(KIND, metadata.toString(), createdAt) {
                alt("User profile for $name")
                initializer()
            }
        }

        fun createNew(
            name: String? = null,
            displayName: String? = null,
            picture: String? = null,
            banner: String? = null,
            website: String? = null,
            about: String? = null,
            nip05: String? = null,
            lnAddress: String? = null,
            lnURL: String? = null,
            pronouns: String? = null,
            twitter: String? = null,
            mastodon: String? = null,
            github: String? = null,
            moneroAddress: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MetadataEvent>.() -> Unit = {},
        ): EventTemplate<MetadataEvent> {
            // Tries to not delete any existing attribute that we do not work with.
            val currentMetadata = JsonNodeFactory.instance.objectNode()

            name?.let { addIfNotBlank(currentMetadata, NameTag.TAG_NAME, it.trim()) }
            displayName?.let { addIfNotBlank(currentMetadata, DisplayNameTag.TAG_NAME, it.trim()) }
            picture?.let { addIfNotBlank(currentMetadata, PictureTag.TAG_NAME, it.trim()) }
            banner?.let { addIfNotBlank(currentMetadata, BannerTag.TAG_NAME, it.trim()) }
            website?.let { addIfNotBlank(currentMetadata, WebsiteTag.TAG_NAME, it.trim()) }
            pronouns?.let { addIfNotBlank(currentMetadata, PronounsTag.TAG_NAME, it.trim()) }
            about?.let { addIfNotBlank(currentMetadata, AboutTag.TAG_NAME, it.trim()) }
            nip05?.let { addIfNotBlank(currentMetadata, Nip05Tag.TAG_NAME, it.trim()) }
            lnAddress?.let { addIfNotBlank(currentMetadata, Lud16Tag.TAG_NAME, it.trim()) }
            lnURL?.let { addIfNotBlank(currentMetadata, Lud06Tag.TAG_NAME, it.trim()) }
            moneroAddress?.let { addIfNotBlank(currentMetadata, MoneroAddressesTag.TAG_NAME, it.trim()) }

            return eventTemplate(KIND, currentMetadata.toString(), createdAt) {
                alt("User profile for ${currentMetadata.get("name")?.asText() ?: "Anonymous"}")

                // For https://github.com/nostr-protocol/nips/pull/1770
                currentMetadata.get(NameTag.TAG_NAME)?.asText()?.let { name(it) }
                currentMetadata.get(DisplayNameTag.TAG_NAME)?.asText()?.let { displayName(it) }
                currentMetadata.get(PictureTag.TAG_NAME)?.asText()?.let { picture(it) }
                currentMetadata.get(BannerTag.TAG_NAME)?.asText()?.let { banner(it) }
                currentMetadata.get(WebsiteTag.TAG_NAME)?.asText()?.let { website(it) }
                currentMetadata.get(PronounsTag.TAG_NAME)?.asText()?.let { pronouns(it) }
                currentMetadata.get(AboutTag.TAG_NAME)?.asText()?.let { about(it) }
                currentMetadata.get(Nip05Tag.TAG_NAME)?.asText()?.let { nip05(it) }
                currentMetadata.get(Lud16Tag.TAG_NAME)?.asText()?.let { lud16(it) }
                currentMetadata.get(Lud06Tag.TAG_NAME)?.asText()?.let { lud06(it) }
                currentMetadata.get(MoneroAddressesTag.TAG_NAME)?.asText()?.let { monero(it) }
                twitter?.let { twitterClaim(it) }
                    ?: mastodon?.let { mastodonClaim(it) }
                github?.let { githubClaim(it) }

                initializer()
            }
        }

        /**
         * Updates fields from the latest Metadata Event. Null params remain unchanged. Empty params get deleted.
         */
        fun updateFromPast(
            latest: MetadataEvent,
            name: String? = null,
            displayName: String? = null,
            picture: String? = null,
            banner: String? = null,
            website: String? = null,
            about: String? = null,
            nip05: String? = null,
            lnAddress: String? = null,
            lnURL: String? = null,
            pronouns: String? = null,
            twitter: String? = null,
            mastodon: String? = null,
            github: String? = null,
            moneroAddress: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MetadataEvent>.() -> Unit = {},
        ): EventTemplate<MetadataEvent> {
            // Tries to not delete any existing attribute that we do not work with.
            val currentMetadata = latest.contactMetadataJson() ?: JsonNodeFactory.instance.objectNode()

            name?.let { addIfNotBlank(currentMetadata, NameTag.TAG_NAME, it.trim()) }
            displayName?.let { addIfNotBlank(currentMetadata, DisplayNameTag.TAG_NAME, it.trim()) }
            picture?.let { addIfNotBlank(currentMetadata, PictureTag.TAG_NAME, it.trim()) }
            banner?.let { addIfNotBlank(currentMetadata, BannerTag.TAG_NAME, it.trim()) }
            website?.let { addIfNotBlank(currentMetadata, WebsiteTag.TAG_NAME, it.trim()) }
            pronouns?.let { addIfNotBlank(currentMetadata, PronounsTag.TAG_NAME, it.trim()) }
            about?.let { addIfNotBlank(currentMetadata, AboutTag.TAG_NAME, it.trim()) }
            nip05?.let { addIfNotBlank(currentMetadata, Nip05Tag.TAG_NAME, it.trim()) }
            lnAddress?.let { addIfNotBlank(currentMetadata, Lud16Tag.TAG_NAME, it.trim()) }
            lnURL?.let { addIfNotBlank(currentMetadata, Lud06Tag.TAG_NAME, it.trim()) }
            moneroAddress?.let { addIfNotBlank(currentMetadata, MoneroAddressesTag.TAG_NAME, it.trim()) }

            val tags =
                latest.tags.builder {
                    alt("User profile for ${currentMetadata.get("name")?.asText() ?: "Anonymous"}")

                    // For https://github.com/nostr-protocol/nips/pull/1770
                    currentMetadata.get(NameTag.TAG_NAME)?.asText()?.let { name(it) } ?: run { remove(NameTag.TAG_NAME) }
                    currentMetadata.get(DisplayNameTag.TAG_NAME)?.asText()?.let { displayName(it) } ?: run { remove(DisplayNameTag.TAG_NAME) }
                    currentMetadata.get(PictureTag.TAG_NAME)?.asText()?.let { picture(it) } ?: run { remove(PictureTag.TAG_NAME) }
                    currentMetadata.get(BannerTag.TAG_NAME)?.asText()?.let { banner(it) } ?: run { remove(BannerTag.TAG_NAME) }
                    currentMetadata.get(WebsiteTag.TAG_NAME)?.asText()?.let { website(it) } ?: run { remove(WebsiteTag.TAG_NAME) }
                    currentMetadata.get(PronounsTag.TAG_NAME)?.asText()?.let { pronouns(it) } ?: run { remove(PronounsTag.TAG_NAME) }
                    currentMetadata.get(AboutTag.TAG_NAME)?.asText()?.let { about(it) } ?: run { remove(AboutTag.TAG_NAME) }
                    currentMetadata.get(Nip05Tag.TAG_NAME)?.asText()?.let { nip05(it) } ?: run { remove(Nip05Tag.TAG_NAME) }
                    currentMetadata.get(Lud16Tag.TAG_NAME)?.asText()?.let { lud16(it) } ?: run { remove(Lud16Tag.TAG_NAME) }
                    currentMetadata.get(Lud06Tag.TAG_NAME)?.asText()?.let { lud06(it) } ?: run { remove(Lud06Tag.TAG_NAME) }
                    currentMetadata.get(MoneroAddressesTag.TAG_NAME)?.asText()?.let { monero(it) } ?: run { remove(MoneroAddressesTag.TAG_NAME) }

                    val newClaims = latest.replaceClaims(twitter, mastodon, github)
                    remove(IdentityClaimTag.TAG_NAME)
                    claims(newClaims)

                    initializer()
                }

            return EventTemplate(createdAt, KIND, tags, currentMetadata.toString())
        }

        private fun addIfNotBlank(
            currentJson: ObjectNode,
            key: String,
            value: String,
        ) {
            if (value.isBlank() || value == "null") {
                currentJson.remove(key)
            } else {
                currentJson.put(key, value.trim())
            }
        }
    }
}
