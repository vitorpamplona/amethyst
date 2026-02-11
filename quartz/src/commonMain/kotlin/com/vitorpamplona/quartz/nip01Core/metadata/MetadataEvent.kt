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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.builder
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent.Companion.updateOrDeleteTagNames
import com.vitorpamplona.quartz.nip01Core.metadata.tags.AboutTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.BannerTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.DisplayNameTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Lud06Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Lud16Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.NameTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.Nip05Tag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.PictureTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.PronounsTag
import com.vitorpamplona.quartz.nip01Core.metadata.tags.WebsiteTag
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.claims
import com.vitorpamplona.quartz.nip39ExtIdentities.githubClaim
import com.vitorpamplona.quartz.nip39ExtIdentities.mastodonClaim
import com.vitorpamplona.quartz.nip39ExtIdentities.replaceClaims
import com.vitorpamplona.quartz.nip39ExtIdentities.twitterClaim
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.text
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull.content
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class MetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun contactMetadataJson() =
        try {
            Json.parseToJsonElement(content) as JsonObject
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MetadataEvent", "Content Parse Error: ${toNostrUri()} ${e.message}")
            null
        }

    fun contactMetaData() =
        try {
            JsonMapper.fromJson<UserMetadata>(content)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MetadataEvent", "Content Parse Error: ${toNostrUri()} ${e.message}")
            null
        }

    companion object {
        const val KIND = 0
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun newUser(
            name: String?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MetadataEvent>.() -> Unit = {},
        ): EventTemplate<MetadataEvent> {
            val metadata = mutableMapOf<String, JsonElement>()
            name?.let { addIfNotBlank(metadata, "name", it) }
            val newJsonObject = JsonObject(metadata)
            val content = Json.encodeToString(JsonObject.serializer(), newJsonObject)

            return eventTemplate(KIND, content, createdAt) {
                alt("User profile for $name")

                updateOrDeleteTagNames(metadata)

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
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MetadataEvent>.() -> Unit = {},
        ): EventTemplate<MetadataEvent> {
            // Tries to not delete any existing attribute that we do not work with.
            val currentMetadata = mutableMapOf<String, JsonElement>()

            updateFieldsWeWorkWith(
                currentMetadata,
                name,
                displayName,
                picture,
                banner,
                website,
                about,
                nip05,
                lnAddress,
                lnURL,
                pronouns,
            )

            val newJsonObject = JsonObject(currentMetadata)
            val content = Json.encodeToString(JsonObject.serializer(), newJsonObject)

            return eventTemplate(KIND, content, createdAt) {
                alt("User profile for ${currentMetadata["name"]?.text ?: "Anonymous"}")

                // For https://github.com/nostr-protocol/nips/pull/1770
                updateOrDeleteTagNames(currentMetadata)

                twitter?.let { twitterClaim(it) }
                mastodon?.let { mastodonClaim(it) }
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
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MetadataEvent>.() -> Unit = {},
        ): EventTemplate<MetadataEvent> {
            // Tries to not delete any existing attribute that we do not work with.
            val currentMetadata = latest.contactMetadataJson()?.toMutableMap() ?: mutableMapOf()

            updateFieldsWeWorkWith(
                currentMetadata,
                name,
                displayName,
                picture,
                banner,
                website,
                about,
                nip05,
                lnAddress,
                lnURL,
                pronouns,
            )

            val newJsonObject = JsonObject(currentMetadata)
            val content = Json.encodeToString(JsonObject.serializer(), newJsonObject)

            val tags =
                latest.tags.builder {
                    alt("User profile for ${currentMetadata["name"]?.text ?: "Anonymous"}")

                    updateOrDeleteTagNames(currentMetadata)

                    val newClaims = latest.replaceClaims(twitter, mastodon, github)
                    remove(IdentityClaimTag.TAG_NAME)
                    claims(newClaims)

                    initializer()
                }

            return EventTemplate(createdAt, KIND, tags, content)
        }

        private fun updateFieldsWeWorkWith(
            currentMetadata: MutableMap<String, JsonElement>,
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
        ) {
            name?.let { addIfNotBlank(currentMetadata, NameTag.TAG_NAME, it) }
            displayName?.let { addIfNotBlank(currentMetadata, DisplayNameTag.TAG_NAME, it) }
            picture?.let { addIfNotBlank(currentMetadata, PictureTag.TAG_NAME, it) }
            banner?.let { addIfNotBlank(currentMetadata, BannerTag.TAG_NAME, it) }
            website?.let { addIfNotBlank(currentMetadata, WebsiteTag.TAG_NAME, it) }
            pronouns?.let { addIfNotBlank(currentMetadata, PronounsTag.TAG_NAME, it) }
            about?.let { addIfNotBlank(currentMetadata, AboutTag.TAG_NAME, it) }
            nip05?.let { addIfNotBlank(currentMetadata, Nip05Tag.TAG_NAME, it) }
            lnAddress?.let { addIfNotBlank(currentMetadata, Lud16Tag.TAG_NAME, it) }
            lnURL?.let { addIfNotBlank(currentMetadata, Lud06Tag.TAG_NAME, it) }
        }

        // For https://github.com/nostr-protocol/nips/pull/1770
        fun TagArrayBuilder<MetadataEvent>.updateOrDeleteTagNames(currentMetadata: MutableMap<String, JsonElement>) {
            currentMetadata[NameTag.TAG_NAME]?.let { name(it.text) } ?: run { remove(NameTag.TAG_NAME) }
            currentMetadata[DisplayNameTag.TAG_NAME]?.let { displayName(it.text) } ?: run { remove(DisplayNameTag.TAG_NAME) }
            currentMetadata[PictureTag.TAG_NAME]?.let { picture(it.text) } ?: run { remove(PictureTag.TAG_NAME) }
            currentMetadata[BannerTag.TAG_NAME]?.let { banner(it.text) } ?: run { remove(BannerTag.TAG_NAME) }
            currentMetadata[WebsiteTag.TAG_NAME]?.let { website(it.text) } ?: run { remove(WebsiteTag.TAG_NAME) }
            currentMetadata[PronounsTag.TAG_NAME]?.let { pronouns(it.text) } ?: run { remove(PronounsTag.TAG_NAME) }
            currentMetadata[AboutTag.TAG_NAME]?.let { about(it.text) } ?: run { remove(AboutTag.TAG_NAME) }
            currentMetadata[Nip05Tag.TAG_NAME]?.let { nip05(it.text) } ?: run { remove(Nip05Tag.TAG_NAME) }
            currentMetadata[Lud16Tag.TAG_NAME]?.let { lud16(it.text) } ?: run { remove(Lud16Tag.TAG_NAME) }
            currentMetadata[Lud06Tag.TAG_NAME]?.let { lud06(it.text) } ?: run { remove(Lud06Tag.TAG_NAME) }
        }

        private fun addIfNotBlank(
            currentJson: MutableMap<String, JsonElement>,
            key: String,
            value: String,
        ) {
            if (value.isBlank() || value == "null") {
                currentJson.remove(key)
            } else {
                currentJson.put(key, JsonPrimitive(value.trim()))
            }
        }
    }
}
