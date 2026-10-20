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
package com.vitorpamplona.amethyst.commons.rendering.unknown

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/**
 * What the `content` of an event of a kind the app has no renderer for looks like, so the fallback
 * card can decide between drawing it as text, as fields, or not at all.
 */
@Immutable
sealed interface ContentShape {
    /** No content, or only whitespace. */
    data object Empty : ContentShape

    /** Words for people: drawn by the rich-text viewer (links, mentions and media work). */
    data class Prose(
        val text: String,
    ) : ContentShape

    /**
     * A JSON document. [fields] are the top-level members of an object with their scalar values
     * (nested objects and arrays are summarized), for drawing as a table; [pretty] is the whole
     * document re-indented, for a code block.
     */
    data class Json(
        val fields: List<JsonField>,
        val isArray: Boolean,
        val size: Int,
        val pretty: String,
    ) : ContentShape

    /** Ciphertext: only the recipients can read it, so nothing of it is shown. */
    data class Encrypted(
        val scheme: EncryptionScheme,
    ) : ContentShape

    /** A blob for software (hex, base64, an unbroken token): shown, at most, as a short code preview. */
    data class Encoded(
        val encoding: BlobEncoding,
        val length: Int,
    ) : ContentShape
}

enum class EncryptionScheme { NIP04, NIP44 }

enum class BlobEncoding { HEX, BASE64, OPAQUE }

/** One top-level member of a JSON object. [nested] when the value is an object or array, summarized in [value]. */
@Immutable
data class JsonField(
    val key: String,
    val value: String,
    val nested: Boolean,
)

@Immutable
data class DigestPerson(
    val pubKey: HexKey,
    val role: String?,
)

@Immutable
data class DigestEventRef(
    val id: HexKey,
    val relay: String?,
    val marker: String?,
    /** True for a NIP-18 `q` quote, false for an `e` reference. */
    val isQuote: Boolean,
)

@Immutable
data class DigestAddressRef(
    val address: Address,
    val relay: String?,
    val marker: String?,
)

@Immutable
data class DigestMedia(
    val url: String,
    val mimeType: String?,
    val alt: String?,
    val dimensions: String?,
    val blurhash: String?,
)

/** A tag the digest has no slot for, as it was written. */
@Immutable
data class RawTag(
    val name: String,
    val values: List<String>,
)

/**
 * Everything the fallback card can say about an event of a kind the app has no renderer for,
 * read from the conventions most kinds share: NIP-31 `alt`, `title`/`name`/`subject`,
 * `summary`/`description`, `image`/`imeta`, `p`/`e`/`q`/`a` references, `t` hashtags, `r` links,
 * the `d` identifier and NIP-40 expiration. Tags it has no slot for stay in [otherTags], in order;
 * `nonce`, `zap` and `-` are left out of it, since the note row around the card already shows them.
 */
@Immutable
class UnknownEventDigest(
    val kind: Int,
    /** English label from quartz's kind registry, or null when no NIP names this kind. */
    val kindName: String?,
    val alt: String?,
    val title: String?,
    val summary: String?,
    val content: ContentShape,
    val media: List<DigestMedia>,
    val people: List<DigestPerson>,
    val events: List<DigestEventRef>,
    val addresses: List<DigestAddressRef>,
    val hashtags: List<String>,
    val links: List<String>,
    val identifier: String?,
    val kinds: List<Int>,
    val relays: List<String>,
    val publishedAt: Long?,
    val startsAt: Long?,
    val endsAt: Long?,
    val expiration: Long?,
    val geohash: String?,
    val location: String?,
    val contentWarning: String?,
    val client: String?,
    val otherTags: List<RawTag>,
    val tagCount: Int,
) {
    /** Text a person wrote about this event: the content when it is prose, else the `alt` description. */
    val readableText: String?
        get() = (content as? ContentShape.Prose)?.text ?: alt

    /** The hashtags the prose content doesn't already show inline as `#tag`, for chips under it. */
    val uncitedHashtags: List<String> by lazy {
        val text = (content as? ContentShape.Prose)?.text ?: return@lazy hashtags
        hashtags.filterNot { text.contains("#$it", ignoreCase = true) }
    }

    companion object {
        fun of(event: Event): UnknownEventDigest {
            var alt: String? = null
            var title: String? = null
            var name: String? = null
            var subject: String? = null
            var summary: String? = null
            var description: String? = null
            var identifier: String? = null
            var publishedAt: Long? = null
            var startsAt: Long? = null
            var endsAt: Long? = null
            var expiration: Long? = null
            var geohash: String? = null
            var location: String? = null
            var contentWarning: String? = null
            var client: String? = null
            val media = ArrayList<DigestMedia>()
            val people = ArrayList<DigestPerson>()
            val events = ArrayList<DigestEventRef>()
            val addresses = ArrayList<DigestAddressRef>()
            val hashtags = ArrayList<String>()
            val links = ArrayList<String>()
            val kinds = ArrayList<Int>()
            val relays = ArrayList<String>()
            val other = ArrayList<RawTag>()

            for (tag in event.tags) {
                if (tag.isEmpty()) continue
                val v = tag.getOrNull(1)?.trim()?.ifEmpty { null }
                val consumed =
                    when (tag[0]) {
                        "alt" -> v?.also { alt = alt ?: it } != null
                        "title" -> v?.also { title = title ?: it } != null
                        "name" -> v?.also { name = name ?: it } != null
                        "subject" -> v?.also { subject = subject ?: it } != null
                        "summary" -> v?.also { summary = summary ?: it } != null
                        "description", "about" -> v?.also { description = description ?: it } != null
                        "d" -> true.also { identifier = identifier ?: v }
                        "image", "thumb", "picture", "banner", "cover" ->
                            v?.takeIf { it.isWebUrl() }?.also { url ->
                                if (media.none { it.url == url }) media += DigestMedia(url, null, null, tag.getOrNull(2), null)
                            } != null
                        "imeta" -> parseImeta(tag)?.also { m -> if (media.none { it.url == m.url }) media += m } != null
                        "p" -> v?.takeIf { Hex.isHex64(it) }?.also { people += DigestPerson(it, tag.getOrNull(3)?.ifBlank { null }) } != null
                        "e", "q" ->
                            v?.takeIf { Hex.isHex64(it) }?.also {
                                events += DigestEventRef(it, tag.getOrNull(2)?.ifBlank { null }, tag.getOrNull(3)?.ifBlank { null }, isQuote = tag[0] == "q")
                            } ?: v?.let { Address.parse(it) }?.also {
                                // `q` may carry an address.
                                addresses += DigestAddressRef(it, tag.getOrNull(2)?.ifBlank { null }, null)
                            } != null
                        "a" -> v?.let { Address.parse(it) }?.also { addresses += DigestAddressRef(it, tag.getOrNull(2)?.ifBlank { null }, tag.getOrNull(3)?.ifBlank { null }) } != null
                        "t" -> v?.also { if (it !in hashtags) hashtags += it } != null
                        "r", "u", "url", "web", "website" -> v?.takeIf { it.isWebUrl() }?.also { if (it !in links) links += it } != null
                        "k" -> v?.toIntOrNull()?.also { if (it !in kinds) kinds += it } != null
                        "relay", "relays" ->
                            tag
                                .drop(1)
                                .filter { it.startsWith("ws") }
                                .also { relays += it }
                                .isNotEmpty()
                        "published_at" -> v?.toLongOrNull()?.also { publishedAt = it } != null
                        "start", "starts" -> v?.toLongOrNull()?.also { startsAt = it } != null
                        "end", "ends" -> v?.toLongOrNull()?.also { endsAt = it } != null
                        "expiration" -> v?.toLongOrNull()?.also { expiration = it } != null
                        "g" -> v?.also { if (geohash == null || it.length > geohash!!.length) geohash = it } != null
                        "location" -> v?.also { location = location ?: it } != null
                        "content-warning" -> true.also { contentWarning = contentWarning ?: v ?: "" }
                        "client" -> v?.also { client = client ?: it } != null
                        // Drawn by every note row already (PoW, zap splits) or meant for relays (NIP-70).
                        "nonce", "zap", "-" -> true
                        else -> false
                    }
                if (!consumed) other += RawTag(tag[0], tag.drop(1))
            }

            return UnknownEventDigest(
                kind = event.kind,
                kindName = KindNames.nameFor(event.kind),
                alt = alt,
                title = title ?: name ?: subject,
                summary = summary ?: description,
                content = classifyContent(event.content),
                media = media,
                people = people.distinctBy { it.pubKey },
                events = events.distinctBy { it.id },
                addresses = addresses.distinctBy { it.address },
                hashtags = hashtags,
                links = links,
                identifier = identifier,
                kinds = kinds,
                relays = relays.distinct(),
                publishedAt = publishedAt,
                startsAt = startsAt,
                endsAt = endsAt,
                expiration = expiration,
                geohash = geohash,
                location = location,
                contentWarning = contentWarning,
                client = client,
                otherTags = other,
                tagCount = event.tags.size,
            )
        }

        private fun parseImeta(tag: Array<String>): DigestMedia? {
            var url: String? = null
            var mime: String? = null
            var alt: String? = null
            var dim: String? = null
            var blurhash: String? = null
            for (i in 1 until tag.size) {
                val entry = tag[i]
                val space = entry.indexOf(' ')
                if (space <= 0) continue
                val value = entry.substring(space + 1).trim()
                when (entry.substring(0, space)) {
                    "url" -> url = url ?: value
                    "m" -> mime = mime ?: value
                    "alt" -> alt = alt ?: value
                    "dim" -> dim = dim ?: value
                    "blurhash" -> blurhash = blurhash ?: value
                }
            }
            return url?.takeIf { it.isWebUrl() }?.let { DigestMedia(it, mime, alt, dim, blurhash) }
        }

        private fun String.isWebUrl() = startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
    }
}

/** True when [event] is of a kind quartz has no class for: EventFactory's plain [Event]. */
fun isUnknownKind(event: Event): Boolean = event::class == Event::class

/**
 * The unknown kinds the cache keeps, so the fallback card can draw them when they are quoted,
 * linked or tagged. Not ephemeral ones (20000-29999): relays don't store them, nothing can link to them.
 */
fun isStorableUnknownKind(event: Event): Boolean = isUnknownKind(event) && event.kind !in 20_000..29_999

/** Long single tokens that are meant to be read: links, invoices, ecash and bech32 entities. */
private val READABLE_TOKEN_PREFIXES =
    listOf("nostr:", "lightning:", "lnbc", "lntb", "lnurl", "lno1", "cashu", "npub1", "nprofile1", "note1", "nevent1", "naddr1", "nrelay1")

private val PRETTY_JSON = Json { prettyPrint = true }

private const val MAX_PRETTY_CHARS = 4_000
private const val MAX_FIELD_CHARS = 160

/**
 * Sorts [content] into one of the [ContentShape]s. The deciding question is "are these words?":
 * natural language has spaces between its words, a payload for software doesn't. So a whitespace-free
 * run is checked for ciphertext, hex and base64, and anything with spaces is prose, unless it is a
 * JSON document or base64 wrapped onto several lines.
 */
fun classifyContent(content: String): ContentShape {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return ContentShape.Empty

    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
        parseJson(trimmed)?.let { return it }
        // "[spam] buy now" is not JSON: fall through to the prose checks.
    }

    val hasSpaces = trimmed.any { it == ' ' || it == '\t' }
    if (!hasSpaces) {
        val oneLine = trimmed.none { it == '\n' || it == '\r' }
        if (oneLine && EncryptedInfo.isNIP04(trimmed)) return ContentShape.Encrypted(EncryptionScheme.NIP04)
        if (READABLE_TOKEN_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) } || trimmed.contains("://")) {
            return ContentShape.Prose(content)
        }
        val joined = if (oneLine) trimmed else trimmed.filterNot { it == '\n' || it == '\r' }
        if (oneLine && isNip44Payload(joined)) return ContentShape.Encrypted(EncryptionScheme.NIP44)
        if (joined.length >= 32 && Hex.isHex(joined)) return ContentShape.Encoded(BlobEncoding.HEX, joined.length)
        if (joined.length >= 64 && joined.all { it.isBase64Char() }) return ContentShape.Encoded(BlobEncoding.BASE64, joined.length)
        if (joined.length >= 120) return ContentShape.Encoded(BlobEncoding.OPAQUE, joined.length)
    }
    // A short word ("gm"), a sentence, a paragraph: words for people.
    return ContentShape.Prose(content)
}

/** NIP-44 v2: base64 of a payload that starts with the version byte 2 and is at least 99 bytes long. */
private fun isNip44Payload(text: String): Boolean {
    // Padding is not required: some clients strip it. Only the version byte decides.
    if (text.length < 132) return false
    if (!text.all { it.isBase64Char() }) return false
    return try {
        Base64.decode(text, 0, 4)[0] == 2.toByte()
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun Char.isBase64Char() = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '+' || this == '/' || this == '=' || this == '-' || this == '_'

private fun parseJson(text: String): ContentShape.Json? {
    val element =
        try {
            Json.parseToJsonElement(text)
        } catch (_: IllegalArgumentException) {
            return null
        }
    val pretty = PRETTY_JSON.encodeToString(JsonElement.serializer(), element).let { if (it.length > MAX_PRETTY_CHARS) it.take(MAX_PRETTY_CHARS) + "\n…" else it }
    return when (element) {
        is JsonObject -> ContentShape.Json(element.map { (k, v) -> jsonField(k, v) }, isArray = false, size = element.size, pretty = pretty)
        is JsonArray -> ContentShape.Json(emptyList(), isArray = true, size = element.size, pretty = pretty)
        else -> null
    }
}

private fun jsonField(
    key: String,
    value: JsonElement,
): JsonField =
    when (value) {
        is JsonNull -> JsonField(key, "null", nested = false)
        is JsonPrimitive -> JsonField(key, value.content.let { if (it.length > MAX_FIELD_CHARS) it.take(MAX_FIELD_CHARS) + "…" else it }, nested = false)
        is JsonArray -> JsonField(key, "[${value.size}]", nested = true)
        is JsonObject -> JsonField(key, "{${value.keys.take(4).joinToString(", ")}${if (value.size > 4) ", …" else ""}}", nested = true)
    }
