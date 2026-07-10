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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.util.isValidUrl
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbhashTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlin.coroutines.cancellation.CancellationException

class RichTextParser {
    fun createMediaContent(
        fullUrl: String,
        eventTags: Map<String, IMetaTag>,
        description: String?,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): MediaUrlContent? {
        val frags = Nip54InlineMetadata().parse(fullUrl)

        val tags = eventTags.get(fullUrl)?.properties ?: emptyMap()

        val contentType = frags[MimeTypeTag.TAG_NAME] ?: tags[MimeTypeTag.TAG_NAME]?.firstOrNull()

        val isImage: Boolean
        val isVideo: Boolean
        val isPdf: Boolean

        if (contentType != null) {
            isImage = contentType.startsWith("image/")
            // HLS playlists are advertised with a non-`video/*` MIME (`application/vnd.apple.mpegurl`
            // and three legacy aliases). Without these, an imeta-described `.m3u8` falls into the
            // null bucket below and the renderer drops back to a plain hyperlink — even though
            // the matching extension would have routed it to MediaUrlVideo. Mirror the canonical
            // list used by MediaItemCache.toExoPlayerMimeType / GalleryThumb.isHlsMimeType.
            isVideo = contentType.startsWith("video/") || contentType.startsWith("audio/") || isHlsMimeType(contentType)
            isPdf = contentType.startsWith("application/pdf")
        } else if (fullUrl.startsWith("data:")) {
            isImage = fullUrl.startsWith("data:image/")
            isVideo = fullUrl.startsWith("data:video/") || fullUrl.startsWith("data:audio/")
            isPdf = fullUrl.startsWith("data:application/pdf")
        } else {
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(fullUrl)
            isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
            isVideo = videoExtensions.any { removedParamsFromUrl.endsWith(it) }
            isPdf = pdfExtensions.any { removedParamsFromUrl.endsWith(it) }
        }

        return if (isImage) {
            MediaUrlImage(
                url = fullUrl,
                description = description ?: frags[AltTag.TAG_NAME] ?: tags[AltTag.TAG_NAME]?.firstOrNull(),
                hash = frags[HashSha256Tag.TAG_NAME] ?: tags[HashSha256Tag.TAG_NAME]?.firstOrNull(),
                blurhash = frags[BlurhashTag.TAG_NAME] ?: tags[BlurhashTag.TAG_NAME]?.firstOrNull(),
                dim = frags[DimensionTag.TAG_NAME]?.let { DimensionTag.parse(it) } ?: tags[DimensionTag.TAG_NAME]?.firstOrNull()?.let { DimensionTag.parse(it) },
                contentWarning = frags[ContentWarningTag.TAG_NAME] ?: tags[ContentWarningTag.TAG_NAME]?.firstOrNull(),
                uri = callbackUri,
                mimeType = contentType,
                thumbhash = frags[ThumbhashTag.TAG_NAME] ?: tags[ThumbhashTag.TAG_NAME]?.firstOrNull(),
                authorPubKey = authorPubKey,
            )
        } else if (isVideo) {
            MediaUrlVideo(
                url = fullUrl,
                description = description ?: frags[AltTag.TAG_NAME] ?: tags[AltTag.TAG_NAME]?.firstOrNull(),
                hash = frags[HashSha256Tag.TAG_NAME] ?: tags[HashSha256Tag.TAG_NAME]?.firstOrNull(),
                blurhash = frags[BlurhashTag.TAG_NAME] ?: tags[BlurhashTag.TAG_NAME]?.firstOrNull(),
                dim = frags[DimensionTag.TAG_NAME]?.let { DimensionTag.parse(it) } ?: tags[DimensionTag.TAG_NAME]?.firstOrNull()?.let { DimensionTag.parse(it) },
                contentWarning = frags[ContentWarningTag.TAG_NAME] ?: tags[ContentWarningTag.TAG_NAME]?.firstOrNull(),
                uri = callbackUri,
                // Poster URL from the imeta's `image` property — downstream gallery-add reads
                // this as the entry's `image` tag so the gallery thumbnail can render the
                // poster JPEG instead of falling back to the blurhash placeholder.
                artworkUri = frags[ImageTag.TAG_NAME] ?: tags[ImageTag.TAG_NAME]?.firstOrNull(),
                mimeType = contentType,
                thumbhash = frags[ThumbhashTag.TAG_NAME] ?: tags[ThumbhashTag.TAG_NAME]?.firstOrNull(),
                authorPubKey = authorPubKey,
            )
        } else if (isPdf) {
            MediaUrlPdf(
                url = fullUrl,
                description = description ?: frags[AltTag.TAG_NAME] ?: tags[AltTag.TAG_NAME]?.firstOrNull(),
                hash = frags[HashSha256Tag.TAG_NAME] ?: tags[HashSha256Tag.TAG_NAME]?.firstOrNull(),
                blurhash = frags[BlurhashTag.TAG_NAME] ?: tags[BlurhashTag.TAG_NAME]?.firstOrNull(),
                dim = frags[DimensionTag.TAG_NAME]?.let { DimensionTag.parse(it) } ?: tags[DimensionTag.TAG_NAME]?.firstOrNull()?.let { DimensionTag.parse(it) },
                uri = callbackUri,
                mimeType = contentType,
                thumbhash = frags[ThumbhashTag.TAG_NAME] ?: tags[ThumbhashTag.TAG_NAME]?.firstOrNull(),
                authorPubKey = authorPubKey,
            )
        } else {
            null
        }
    }

    fun fixMissingSpaces(
        input: String,
        urlList: Set<String>,
    ): String {
        if (urlList.isEmpty()) return input

        // Walk the text, and wherever one of the detected URLs sits glued to a
        // non-space/non-newline neighbour, insert a single separating space so the
        // word-by-word segmenter downstream can recognise it as a standalone URL.
        //
        // This used to be a `Regex("([^ \n])?($escapedWords)([^ \n])?")` replace,
        // but Kotlin/Native's regex engine mishandles the optional capture groups
        // `([^ \n])?` (it fails to backtrack them to zero width), corrupting every
        // URL on iOS — e.g. "https://x" came back as "h https://x". A direct scan
        // sidesteps the engine entirely and is platform-independent.
        //
        // This runs on the main thread per rendered note, so it stays linear in the
        // text length: URLs are bucketed by their first character, and the inner
        // match attempt only fires at positions whose character can actually start
        // a URL — every other character costs a single map lookup. Within a bucket
        // the URLs are kept longest-first so a URL that is a prefix of a longer one
        // never shadows it.
        val byFirstChar = HashMap<Char, MutableList<String>>()
        urlList
            .asSequence()
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
            .forEach { byFirstChar.getOrPut(it[0]) { ArrayList(1) }.add(it) }

        val result = StringBuilder(input.length)
        val length = input.length
        var i = 0
        while (i < length) {
            val candidates = byFirstChar[input[i]]
            var match: String? = null
            if (candidates != null) {
                for (url in candidates) {
                    if (input.startsWith(url, i)) {
                        match = url
                        break
                    }
                }
            }

            if (match != null) {
                // Separate from a glued prefix character.
                if (result.isNotEmpty()) {
                    val prev = result[result.length - 1]
                    if (prev != ' ' && prev != '\n') result.append(' ')
                }

                result.append(match)
                i += match.length

                // Separate from a glued suffix character.
                if (i < length) {
                    val next = input[i]
                    if (next != ' ' && next != '\n') result.append(' ')
                }
            } else {
                result.append(input[i])
                i++
            }
        }

        return result.toString()
    }

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String? = null,
    ): RichTextViewerState {
        val imetas = tags.lists.imetasByUrl()
        val urlSet = UrlParser().parseValidUrls(content)

        val mediaContents =
            urlSet.withScheme.mapNotNull { fullUrl ->
                createMediaContent(fullUrl, imetas, content, callbackUri, authorPubKey)
            } +
                urlSet.withoutScheme.mapNotNull { fullUrl ->
                    createMediaContent(fullUrl, imetas, content, callbackUri, authorPubKey)
                }

        val mediaForPager = mediaContents.associateBy { it.url }

        val imageUrls = mediaForPager.filterValues { it is MediaUrlImage }.keys
        val videoUrls = mediaForPager.filterValues { it is MediaUrlVideo }.keys
        val pdfUrls = mediaForPager.filterValues { it is MediaUrlPdf }.keys

        val emojiMap = CustomEmoji.createEmojiMap(tags.lists)

        val allUrls = urlSet.withScheme + urlSet.withoutScheme + urlSet.emails + urlSet.bech32s + urlSet.relayUrls + urlSet.blossomUris + urlSet.groupLinks

        val newContent = fixMissingSpaces(content, allUrls)

        val segments = findTextSegments(newContent, imageUrls, videoUrls, pdfUrls, urlSet, emojiMap, tags)

        val mediaForPagerWithBase64 =
            mediaForPager +
                segments
                    .flatMap { paragraph ->
                        paragraph.words
                            .mapNotNull {
                                if (it is Base64Segment) {
                                    createMediaContent(it.segmentText, emptyMap(), content, callbackUri)
                                } else if (it is BlossomUriSegment) {
                                    createMediaContent(it.segmentText, emptyMap(), content, callbackUri)
                                } else {
                                    null
                                }
                            }
                    }.associateBy { it.url }

        return RichTextViewerState(
            urlSet = urlSet,
            mediaForPager = mediaForPagerWithBase64.toImmutableMap(),
            mediaList = mediaForPagerWithBase64.values.toImmutableList(),
            customEmoji = emojiMap.toImmutableMap(),
            paragraphs = segments,
            tags = tags,
        )
    }

    private fun findTextSegments(
        content: String,
        images: Set<String>,
        videos: Set<String>,
        pdfs: Set<String>,
        urls: Urls,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ImmutableList<ParagraphState> {
        // Trailing spaces and newlines would otherwise produce empty trailing
        // paragraphs, each rendered as a blank line between the last visible
        // word and the end of the component.
        val trimmedContent = content.trimEnd()
        if (trimmedContent.isEmpty()) return persistentListOf()

        val lines = trimmedContent.split('\n')
        val paragraphSegments = ArrayList<ParagraphState>(lines.size)

        lines.forEach { paragraph ->
            val isRTL = isArabic(paragraph)

            // split() behaves like `line.split(' ')`, but keeps math spans
            // (`$...$`, `$$...$$`) whole instead of tearing them at internal spaces.
            val segments =
                MathParser.split(paragraph.trimEnd()).map { token ->
                    when (token) {
                        is MathParser.Token.Math -> MathSegment(token.raw, token.latex, token.displayMode, token.leading, token.trailing)
                        is MathParser.Token.Word -> wordIdentifier(token.text, images, videos, pdfs, urls, emojis, tags)
                    }
                }

            paragraphSegments.add(ParagraphState(segments.toPersistentList(), isRTL))
        }

        val segmentsWithGalleries = GalleryParser().processParagraphs(paragraphSegments)

        return segmentsWithGalleries
            .map { paragraph ->
                if (paragraph.words.isEmpty() || paragraph.words.any { it !is RegularTextSegment }) {
                    paragraph
                } else {
                    ParagraphState(
                        persistentListOf<Segment>(RegularTextSegment(paragraph.words.joinToString(" ") { it.segmentText })),
                        paragraph.isRTL,
                    )
                }
            }.toImmutableList()
    }

    private fun isPhoneNumberChar(c: Char): Boolean =
        when (c) {
            in '0'..'9' -> true
            '-' -> true
            ' ' -> true
            '.' -> true
            else -> false
        }

    fun isPotentialPhoneNumber(word: String): Boolean {
        if (word.length !in 7..14) return false
        var isPotentialNumber = true

        for (c in word) {
            if (!isPhoneNumberChar(c)) {
                isPotentialNumber = false
                break
            }
        }
        return isPotentialNumber
    }

    fun isDate(word: String): Boolean = shortDatePattern.matches(word) || longDatePattern.matches(word)

    private fun isArabic(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

    private fun wordIdentifier(
        word: String,
        images: Set<String>,
        videos: Set<String>,
        pdfs: Set<String>,
        urls: Urls,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        if (word.isEmpty()) return RegularTextSegment(word)

        if (word.startsWith("data:image/")) {
            if (Patterns.BASE64_IMAGE.matches(word)) return Base64Segment(word)
        }

        if (images.contains(word)) {
            return if (urls.withoutScheme.contains(word)) {
                ImageSegment("https://$word")
            } else {
                ImageSegment(word)
            }
        }

        if (videos.contains(word)) {
            return if (urls.withoutScheme.contains(word)) {
                VideoSegment("https://$word")
            } else {
                VideoSegment(word)
            }
        }

        if (pdfs.contains(word)) {
            return if (urls.withoutScheme.contains(word)) {
                PdfSegment("https://$word")
            } else {
                PdfSegment(word)
            }
        }

        if (urls.withoutScheme.contains(word)) {
            parseNowhereLink(word)?.let { return it }
            return SchemelessUrlSegment(word)
        }

        if (urls.withScheme.contains(word)) {
            parseNowhereLink(word)?.let { return it }
            return LinkSegment(word)
        }

        if (urls.emails.contains(word)) return EmailSegment(word)

        if (urls.bech32s.contains(word)) return BechSegment(word)

        // Checked before relayUrls: a group link `wss://relay'groupId` embeds a relay URL,
        // so it must win over the bare relay-URL interpretation.
        if (urls.groupLinks.contains(word)) return RelayGroupLinkSegment(word)

        if (urls.relayUrls.contains(word)) return RelayUrlSegment(word)

        if (urls.blossomUris.contains(word)) return BlossomUriSegment(word)

        if (startsWithNIP19Scheme(word)) return BechSegment(word)

        if (CustomEmoji.fastMightContainEmoji(word, emojis) && emojis.any { word.contains(it.key) }) return EmojiSegment(word)

        if (word.startsWith("lnbc", true)) return InvoiceSegment(word)

        if (word.startsWith("lnurl", true)) return WithdrawSegment(word)

        if (word.startsWith("cashuA", true) || word.startsWith("cashuB", true)) return CashuSegment(word)

        if (word.startsWith("noffer1", true)) {
            (ClinkPointerParser.parse(word) as? NOffer)?.let { return ClinkOfferSegment(word, it) }
        }

        if (word.startsWith('#')) return parseHash(word, tags)

        if (EmojiCoder.isCoded(word)) return SecretEmoji(word)

        if (isPotentialPhoneNumber(word) && !isDate(word)) {
            if (Patterns.PHONE.matches(word)) return PhoneSegment(word)
        }

        return RegularTextSegment(word)
    }

    private fun parseHash(
        word: String,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        // First #[n]
        try {
            val matcher = tagIndex.find(word)
            if (matcher != null) {
                val index = matcher.groups[1]?.value?.toInt()
                val suffix = matcher.groups[2]?.value

                if (index != null && index >= 0 && index < tags.lists.size) {
                    val tag = tags.lists[index]

                    if (tag.size > 1) {
                        if (tag[0] == "p") {
                            return HashIndexUserSegment(word, tag[1], suffix)
                        } else if (tag[0] == "e" || tag[0] == "a") {
                            return HashIndexEventSegment(word, tag[1], suffix)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("Tag Parser", "Couldn't link tag $word", e)
        }

        // Second #Amethyst
        try {
            val hashtagMatcher = hashTagsPattern.find(word)
            if (hashtagMatcher != null) {
                val hashtag = hashtagMatcher.groups[1]?.value
                if (hashtag != null) {
                    return HashTagSegment(word, hashtag, hashtagMatcher.groups[2]?.value?.ifEmpty { null })
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
        }

        return RegularTextSegment(word)
    }

    companion object {
        val longDatePattern: Regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val shortDatePattern: Regex = Regex("^\\d{2}-\\d{2}-\\d{2}$")

        val noProtocolUrlValidator =
            Regex(
                "(([a-zA-Z0-9_-]+@)?([a-zA-Z0-9_-]+\\.)*[a-zA-Z0-9_-]+[\\.\\:][a-zA-Z0-9_]+([\\/ \\?\\=\\&\\#\\.]?[a-zA-Z0-9_-]+)*\\/?)(.*)",
            )

        val imageExt = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg", "avif")
        val videoExt = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "mp3", "m3u8", "ogg", "wav", "flac", "aac", "opus", "m4a", "f4a")
        val pdfExt = listOf("pdf")

        val imageExtensions = imageExt + imageExt.map { it.uppercase() }
        val videoExtensions = videoExt + videoExt.map { it.uppercase() }
        val pdfExtensions = pdfExt + pdfExt.map { it.uppercase() }

        val tagIndex = Regex("\\#\\[([0-9]+)\\](.*)")
        val hashTagsPattern: Regex =
            Regex("#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)(.*)", RegexOption.IGNORE_CASE)

        val acceptedNIP19schemes =
            listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed") +
                listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed").map {
                    it.uppercase()
                }

        private fun removeQueryParamsForExtensionComparison(fullUrl: String): String =
            if (fullUrl.contains("?")) {
                fullUrl.split("?")[0]
            } else if (fullUrl.contains("#")) {
                fullUrl.split("#")[0]
            } else {
                fullUrl
            }

        fun isImageExtension(ext: String) = imageExtensions.any { it == ext }

        fun isImageOrVideoExtension(ext: String) = imageExtensions.any { it == ext } || videoExtensions.any { it == ext }

        fun isImageOrVideoUrl(url: String): Boolean {
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(url)

            return imageExtensions.any { removedParamsFromUrl.endsWith(it) } ||
                videoExtensions.any { removedParamsFromUrl.endsWith(it) }
        }

        fun isImageUrl(url: String): Boolean {
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(url)
            return imageExtensions.any { removedParamsFromUrl.endsWith(it) }
        }

        fun isVideoUrl(url: String): Boolean {
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(url)
            return videoExtensions.any { removedParamsFromUrl.endsWith(it) }
        }

        // Mirrors the canonical HLS-playlist MIME list also kept in MediaItemCache.toExoPlayerMimeType.
        // Called per URL during feed render — uses `equals(ignoreCase)` instead of `lowercase()` to
        // avoid a per-call String allocation on the common non-HLS path.
        fun isHlsMimeType(mimeType: String?): Boolean {
            if (mimeType == null) return false
            return mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
                mimeType.equals("application/x-mpegurl", ignoreCase = true) ||
                mimeType.equals("audio/x-mpegurl", ignoreCase = true) ||
                mimeType.equals("audio/mpegurl", ignoreCase = true)
        }

        fun isPdfUrl(url: String): Boolean {
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(url)
            return pdfExtensions.any { removedParamsFromUrl.endsWith(it) }
        }

        fun isValidURL(url: String?): Boolean = isValidUrl(url)

        fun parseImageOrVideo(fullUrl: String): BaseMediaContent {
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(fullUrl)
            val isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
            val isVideo = videoExtensions.any { removedParamsFromUrl.endsWith(it) }

            return if (isImage) {
                MediaUrlImage(fullUrl)
            } else if (isVideo) {
                MediaUrlVideo(fullUrl)
            } else {
                MediaUrlImage(fullUrl)
            }
        }

        fun startsWithNIP19Scheme(word: String): Boolean {
            if (word.isEmpty()) return false
            return if (word[0] == 'n' || word[0] == 'N') {
                if (word.startsWith("nostr:n") || word.startsWith("NOSTR:N")) {
                    acceptedNIP19schemes.any { word.startsWith(it, 6) }
                } else {
                    acceptedNIP19schemes.any { word.startsWith(it) }
                }
            } else if (word[0] == '@') {
                acceptedNIP19schemes.any { word.startsWith(it, 1) }
            } else {
                false
            }
        }

        fun isUrlWithoutScheme(url: String) = noProtocolUrlValidator.matches(url)

        // Nowhere links (https://github.com/5t34k/nowhere) encode an entire mini-site in the URL
        // fragment. Servers never see the fragment, so OpenGraph previews return nothing useful
        // (and hostednowhere.com 403s scrapers). Detect them by host + presence of a fragment
        // so the renderer can show a branded card instead of falling through to LoadUrlPreview.
        private val nowhereHosts = listOf("nowhr.xyz", "hostednowhere.com")

        fun parseNowhereLink(word: String): NowhereLinkSegment? {
            val afterScheme =
                when {
                    word.startsWith("https://", ignoreCase = true) -> word.substring(8)
                    word.startsWith("http://", ignoreCase = true) -> word.substring(7)
                    else -> word
                }
            val slash = afterScheme.indexOf('/')
            if (slash < 0) return null
            val host = afterScheme.substring(0, slash).lowercase()
            if (host !in nowhereHosts) return null
            val hash = afterScheme.indexOf('#', slash)
            if (hash < 0) return null
            val pathSegment = afterScheme.substring(slash + 1, hash).substringBefore('/').takeIf { it.isNotEmpty() }
            return NowhereLinkSegment(word, host, pathSegment)
        }
    }
}

val mimeTypeMap: Map<String, String> =
    mapOf(
        // Images
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
        "tiff" to "image/tiff",
        // Video
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "ogg" to "video/ogg",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mkv" to "video/x-matroska",
        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "f4a" to "audio/mp4",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        // Documents
        "pdf" to "application/pdf",
    )
