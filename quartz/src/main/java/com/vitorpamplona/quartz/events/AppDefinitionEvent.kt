/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.fasterxml.jackson.annotation.JsonProperty
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import java.io.ByteArrayInputStream

@Stable
class AppMetadata {
    var name: String? = null
    var username: String? = null

    @JsonProperty("display_name")
    var displayName: String? = null
    var picture: String? = null

    var banner: String? = null
    var image: String? = null
    var website: String? = null
    var about: String? = null
    var subscription: Boolean? = false
    var cashuAccepted: Boolean? = false
    var encryptionSupported: Boolean? = false
    var personalized: Boolean? = false
    var amount: String? = null

    var nip05: String? = null
    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    var twitter: String? = null

    @Transient
    var tags: ImmutableListOfLists<String>? = null

    fun countMemory(): Long =
        20 * pointerSizeInBytes + // 20 fields, 4 bytes for each reference
            (name?.bytesUsedInMemory() ?: 0L) +
            (username?.bytesUsedInMemory() ?: 0L) +
            (displayName?.bytesUsedInMemory() ?: 0L) +
            (picture?.bytesUsedInMemory() ?: 0L) +
            (banner?.bytesUsedInMemory() ?: 0L) +
            (image?.bytesUsedInMemory() ?: 0L) +
            (website?.bytesUsedInMemory() ?: 0L) +
            (about?.bytesUsedInMemory() ?: 0L) +
            (subscription?.bytesUsedInMemory() ?: 0L) +
            (cashuAccepted?.bytesUsedInMemory() ?: 0L) +
            (encryptionSupported?.bytesUsedInMemory() ?: 0L) +
            (personalized?.bytesUsedInMemory() ?: 0L) + // A Boolean has 8 bytes of header, plus 1 byte of payload, for a total of 9 bytes of information. The JVM then rounds it up to the next multiple of 8. so the one instance of java.lang.Boolean takes up 16 bytes of memory.
            (amount?.bytesUsedInMemory() ?: 0L) +
            (nip05?.bytesUsedInMemory() ?: 0L) +
            (domain?.bytesUsedInMemory() ?: 0L) +
            (lud06?.bytesUsedInMemory() ?: 0L) +
            (lud16?.bytesUsedInMemory() ?: 0L) +
            (twitter?.bytesUsedInMemory() ?: 0L) +
            (tags?.lists?.sumOf { it.sumOf { it.bytesUsedInMemory() } } ?: 0L)

    fun anyName(): String? = displayName ?: name ?: username

    fun anyNameStartsWith(prefix: String): Boolean =
        listOfNotNull(name, username, displayName, nip05, lud06, lud16).any {
            it.contains(prefix, true)
        }

    fun lnAddress(): String? = lud16 ?: lud06

    fun bestName(): String? = displayName ?: name ?: username

    fun nip05(): String? = nip05

    fun profilePicture(): String? = picture

    fun cleanBlankNames() {
        if (picture?.isNotEmpty() == true) picture = picture?.trim()
        if (nip05?.isNotEmpty() == true) nip05 = nip05?.trim()

        if (displayName?.isNotEmpty() == true) displayName = displayName?.trim()
        if (name?.isNotEmpty() == true) name = name?.trim()
        if (username?.isNotEmpty() == true) username = username?.trim()
        if (lud06?.isNotEmpty() == true) lud06 = lud06?.trim()
        if (lud16?.isNotEmpty() == true) lud16 = lud16?.trim()

        if (website?.isNotEmpty() == true) website = website?.trim()
        if (domain?.isNotEmpty() == true) domain = domain?.trim()

        if (picture?.isBlank() == true) picture = null
        if (nip05?.isBlank() == true) nip05 = null
        if (displayName?.isBlank() == true) displayName = null
        if (name?.isBlank() == true) name = null
        if (username?.isBlank() == true) username = null
        if (lud06?.isBlank() == true) lud06 = null
        if (lud16?.isBlank() == true) lud16 = null

        if (website?.isBlank() == true) website = null
        if (domain?.isBlank() == true) domain = null
    }
}

@Immutable
class AppDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun countMemory(): Long = super.countMemory() + (cachedMetadata?.countMemory() ?: 8L)

    @Transient private var cachedMetadata: AppMetadata? = null

    fun appMetaData() =
        if (cachedMetadata != null) {
            cachedMetadata
        } else {
            try {
                val newMetadata =
                    mapper.readValue(
                        ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
                        AppMetadata::class.java,
                    )

                cachedMetadata = newMetadata

                newMetadata
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w("AppDefinitionEvent", "Content Parse Error: ${toNostrUri()} ${e.localizedMessage}")
                null
            }
        }

    fun supportedKinds() =
        tags
            .filter { it.size > 1 && it[0] == "k" }
            .mapNotNull { runCatching { it[1].toInt() }.getOrNull() }

    fun includeKind(kind: String) = tags.any { it.size > 1 && it[0] == "k" && it[1] == kind }

    fun publishedAt() = tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)

    companion object {
        const val KIND = 31990

        fun create(
            details: UserMetadata,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AppDefinitionEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("alt", "App definition event for ${details.name}"),
                )
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
