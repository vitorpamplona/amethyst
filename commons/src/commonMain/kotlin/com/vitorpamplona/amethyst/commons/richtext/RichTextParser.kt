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

import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentList
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

class RichTextParser {
    fun createMediaContent(
        fullUrl: String,
        eventTags: Map<String, IMetaTag>,
        description: String?,
        callbackUri: String? = null,
    ): MediaUrlContent? {
        val frags = Nip54InlineMetadata().parse(fullUrl)

        val tags = eventTags.get(fullUrl)?.properties ?: emptyMap()

        val contentType = frags[MimeTypeTag.TAG_NAME] ?: tags[MimeTypeTag.TAG_NAME]?.firstOrNull()

        val isImage: Boolean
        val isVideo: Boolean

        if (contentType != null) {
            isImage = contentType.startsWith("image/")
            isVideo = contentType.startsWith("video/")
        } else if (fullUrl.startsWith("data:")) {
            isImage = fullUrl.startsWith("data:image/")
            isVideo = fullUrl.startsWith("data:video/")
        } else {
            val removedParamsFromUrl = stripUrlForExtensionCheck(fullUrl)
            isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
            isVideo = videoExtensions.any { removedParamsFromUrl.endsWith(it) }
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
                mimeType = contentType,
            )
        } else {
            null
        }
    }

    fun parseValidUrls(content: String): LinkedHashSet<String> {
        val urls = UrlDetector(content, UrlDetectorOptions.Default).detect()

        return urls.mapNotNullTo(LinkedHashSet(urls.size)) {
            if (it.originalUrl.contains("@")) {
                if (Patterns.EMAIL_ADDRESS.matches(it.originalUrl)) {
                    null
                } else {
                    it.originalUrl
                }
            } else if (isNumber(it.originalUrl)) {
                null // avoids urls that look like 123.22
            } else if (it.originalUrl.contains("。")) {
                null // avoids Japanese characters as fake urls
            } else {
                if (HTTPRegex.matches(it.originalUrl)) {
                    it.originalUrl
                } else {
                    null
                }
            }
        }
    }

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
    ): RichTextViewerState {
        val imetas = tags.lists.imetasByUrl()
        val urlSet = parseValidUrls(content)

        val imagesForPager =
            urlSet.mapNotNull { fullUrl -> createMediaContent(fullUrl, imetas, content, callbackUri) }.associateBy { it.url }

        val imageUrls = imagesForPager.filterValues { it is MediaUrlImage }.keys
        val videoUrls = imagesForPager.filterValues { it is MediaUrlVideo }.keys

        val emojiMap = CustomEmoji.createEmojiMap(tags.lists)

        val segments = findTextSegments(content, imageUrls, videoUrls, urlSet, emojiMap, tags)

        val base64Images = segments.map { it.words.filterIsInstance<Base64Segment>() }.flatten()

        val imagesForPagerWithBase64 =
            imagesForPager +
                base64Images
                    .mapNotNull { createMediaContent(it.segmentText, emptyMap(), content, callbackUri) }
                    .associateBy { it.url }

        return RichTextViewerState(
            urlSet.toImmutableSet(),
            imagesForPagerWithBase64.toImmutableMap(),
            imagesForPagerWithBase64.values.toImmutableList(),
            emojiMap.toImmutableMap(),
            segments,
            tags,
        )
    }

    private fun findTextSegments(
        content: String,
        images: Set<String>,
        videos: Set<String>,
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ImmutableList<ParagraphState> {
        val paragraphs = ArrayList<ParagraphState>()
        var lineStart = 0

        // Scan for newline boundaries without splitting the string into a List<String>.
        while (lineStart <= content.length) {
            val nlIdx = content.indexOf('\n', lineStart)
            val lineEnd = if (nlIdx < 0) content.length else nlIdx
            paragraphs.add(parseParagraphLine(content, lineStart, lineEnd, images, videos, urls, emojis, tags))
            lineStart = lineEnd + 1
        }

        return GalleryParser()
            .processParagraphs(paragraphs)
            .map { mergeIfAllRegular(it) }
            .toImmutableList()
    }

    /**
     * Parses a single line of [content] (from [lineStart] to [lineEnd], exclusive) into a
     * [ParagraphState]. Trailing whitespace is ignored. For lines where every word is plain text,
     * a single [RegularTextSegment] is returned without allocating per-word substrings.
     */
    private fun parseParagraphLine(
        content: String,
        lineStart: Int,
        lineEnd: Int,
        images: Set<String>,
        videos: Set<String>,
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ParagraphState {
        val isRTL = isArabicInRange(content, lineStart, lineEnd)

        // Strip trailing whitespace without creating a substring.
        var actualEnd = lineEnd
        while (actualEnd > lineStart && content[actualEnd - 1].isWhitespace()) actualEnd--

        if (actualEnd <= lineStart) {
            // Empty or all-whitespace line.
            return ParagraphState(persistentListOf(RegularTextSegment("")), isRTL)
        }

        // Fast path: if every word is plain text, return one segment for the whole line.
        if (isAllPlainText(content, lineStart, actualEnd, emojis)) {
            return ParagraphState(
                persistentListOf(RegularTextSegment(content.substring(lineStart, actualEnd))),
                isRTL,
            )
        }

        // Mixed line: classify each space-delimited word individually.
        val segments = ArrayList<Segment>()
        var wordStart = lineStart
        while (wordStart < actualEnd) {
            val spIdx = content.indexOf(' ', wordStart)
            val wordEnd = if (spIdx < 0 || spIdx >= actualEnd) actualEnd else spIdx
            segments.add(wordIdentifier(content.substring(wordStart, wordEnd), images, videos, urls, emojis, tags))
            wordStart = wordEnd + 1
        }
        return ParagraphState(segments.toPersistentList(), isRTL)
    }

    /**
     * Returns true when every space-delimited word in content[lineStart, lineEnd) is plain text
     * that needs no special rendering. A word is plain if [isPlainWord] returns true for it.
     */
    private fun isAllPlainText(
        content: String,
        lineStart: Int,
        lineEnd: Int,
        emojis: Map<String, String>,
    ): Boolean {
        var wordStart = lineStart
        while (wordStart < lineEnd) {
            val spIdx = content.indexOf(' ', wordStart)
            val wordEnd = if (spIdx < 0 || spIdx >= lineEnd) lineEnd else spIdx
            if (!isPlainWord(content, wordStart, wordEnd, emojis)) return false
            wordStart = wordEnd + 1
        }
        return true
    }

    /**
     * If [paragraph] contains only [RegularTextSegment] words, merges them back into a single
     * segment joined by spaces. This collapses paragraphs that were split word-by-word but turned
     * out to be entirely plain text (e.g. after gallery post-processing).
     */
    private fun mergeIfAllRegular(paragraph: ParagraphState): ParagraphState {
        if (paragraph.words.size <= 1) return paragraph
        if (paragraph.words.any { it !is RegularTextSegment }) return paragraph
        return ParagraphState(
            persistentListOf(RegularTextSegment(paragraph.words.joinToString(" ") { it.segmentText })),
            paragraph.isRTL,
        )
    }

    /**
     * Returns true when the word at content[wordStart, wordEnd) is definitely plain text and does
     * not need a full [wordIdentifier] classification. Conservative: returning false only means
     * the caller should run the full check; it never produces a wrong result.
     */
    private fun isPlainWord(
        content: String,
        wordStart: Int,
        wordEnd: Int,
        emojis: Map<String, String>,
    ): Boolean {
        val len = wordEnd - wordStart
        if (len == 0) return true

        val c0 = content[wordStart]

        // Quick first-character reject for token types that always start with a known character.
        when (c0) {
            '#' -> return false // hashtag (#hashtag) or tag reference (#[n])
            '@' -> return false // @npub… NIP-19 mention
            'd', 'D' -> if (len > 11 && content.startsWith("data:image/", wordStart)) return false
            'l', 'L' -> {
                if (len > 4 && content.startsWith("lnbc", wordStart, ignoreCase = true)) return false
                if (len > 5 && content.startsWith("lnurl", wordStart, ignoreCase = true)) return false
            }
            'c', 'C' -> {
                if (len > 6 &&
                    (
                        content.startsWith("cashuA", wordStart, ignoreCase = true) ||
                            content.startsWith("cashuB", wordStart, ignoreCase = true)
                    )
                ) return false
            }
            'n', 'N' -> {
                // nostr: prefix or NIP-19 bech32 schemes (npub1, note1, naddr1, nevent1, nprofile1, nembed)
                if (len >= 5) {
                    if (content.startsWith("nostr:", wordStart, ignoreCase = true)) return false
                    if (wordStart + 1 < wordEnd) {
                        when (content[wordStart + 1]) {
                            'p', 'P' ->
                                if (content.startsWith("npub1", wordStart, ignoreCase = true) ||
                                    content.startsWith("nprofile1", wordStart, ignoreCase = true)
                                ) return false
                            'o', 'O' -> if (content.startsWith("note1", wordStart, ignoreCase = true)) return false
                            'a', 'A' -> if (content.startsWith("naddr1", wordStart, ignoreCase = true)) return false
                            'e', 'E' ->
                                if (content.startsWith("nevent1", wordStart, ignoreCase = true) ||
                                    content.startsWith("nembed", wordStart, ignoreCase = true)
                                ) return false
                        }
                    }
                }
            }
            'h', 'H' -> {
                // Only http(s):// words can be in the URL sets (parseValidUrls filters by HTTPRegex).
                if (len >= 7 &&
                    (
                        content.startsWith("http://", wordStart, ignoreCase = true) ||
                            content.startsWith("https://", wordStart, ignoreCase = true)
                    )
                ) return false
            }
        }

        // Single-pass character scan: detect markers that require full classification.
        var isPotentialPhone = len in 7..14
        var hasMidPeriod = false
        for (i in wordStart until wordEnd) {
            val c = content[i]
            val code = c.code
            when {
                c == ':' && emojis.isNotEmpty() -> return false // custom emoji :name: format
                c == '@' -> return false // email address
                c == '.' && i > wordStart && i < wordEnd - 1 -> hasMidPeriod = true // possible schemeless URL
                code in 0xFE00..0xFE0F -> return false // Unicode variation selectors (EmojiCoder)
                code == 0xDB40 -> return false // high surrogate for variation-selector supplement (EmojiCoder)
                isPotentialPhone && c !in '0'..'9' && c != '-' && c != ' ' && c != '.' -> isPotentialPhone = false
            }
        }

        // Defer to wordIdentifier to confirm whether these are actually a phone / schemeless URL.
        if (isPotentialPhone) return false
        if (hasMidPeriod) return false

        return true
    }

    private fun isArabicInRange(
        content: String,
        start: Int,
        end: Int,
    ): Boolean {
        for (i in start until end) {
            val c = content[i]
            if (c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F') return true
        }
        return false
    }

    private fun isNumber(word: String) = numberPattern.matches(word)

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

    private fun wordIdentifier(
        word: String,
        images: Set<String>,
        videos: Set<String>,
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        if (word.isEmpty()) return RegularTextSegment(word)

        // Inline media and detected URL sets
        if (word.startsWith("data:image/")) {
            if (Patterns.BASE64_IMAGE.matches(word)) return Base64Segment(word)
        }
        if (images.contains(word)) return ImageSegment(word)
        if (videos.contains(word)) return VideoSegment(word)
        if (urls.contains(word)) return LinkSegment(word)

        // Custom emoji
        if (CustomEmoji.fastMightContainEmoji(word, emojis) && emojis.any { word.contains(it.key) }) return EmojiSegment(word)

        // Lightning / Cashu payments
        if (word.startsWith("lnbc", true)) return InvoiceSegment(word)
        if (word.startsWith("lnurl", true)) return WithdrawSegment(word)
        if (word.startsWith("cashuA", true) || word.startsWith("cashuB", true)) return CashuSegment(word)

        // Nostr / NIP-19 identifiers
        if (word.startsWith("#")) return parseHash(word, tags)
        if (EmojiCoder.isCoded(word)) return SecretEmoji(word)
        if (startsWithNIP19Scheme(word)) return BechSegment(word)

        // Contact info
        if (word.contains("@")) {
            if (Patterns.EMAIL_ADDRESS.matches(word)) return EmailSegment(word)
        }
        if (isPotentialPhoneNumber(word) && !isDate(word)) {
            if (Patterns.PHONE.matches(word)) return PhoneSegment(word)
        }

        // Schemeless URL fallback
        return parseSchemelessUrl(word) ?: RegularTextSegment(word)
    }

    private fun parseSchemelessUrl(word: String): SchemelessUrlSegment? {
        val indexOfPeriod = word.indexOf('.')
        if (indexOfPeriod <= 0 || indexOfPeriod >= word.length - 1) return null
        val schemelessMatcher = noProtocolUrlValidator.find(word) ?: return null
        val url = schemelessMatcher.groups[1]?.value ?: return null
        val additionalChars = schemelessMatcher.groups[4]?.value?.ifEmpty { null }
        if (additionalUrlSchema.find(word) == null) return null
        return SchemelessUrlSegment(word, url, additionalChars)
    }

    private fun parseHash(
        word: String,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        // First #[n] — tag index reference
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

        // Second #Amethyst — plain hashtag
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
        // --- Date / number patterns ---
        val longDatePattern: Regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val shortDatePattern: Regex = Regex("^\\d{2}-\\d{2}-\\d{2}$")
        val numberPattern: Regex = Regex("^(-?[\\d.]+)([a-zA-Z%]*)$")

        // --- File extension lists ---
        val imageExt = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg", "avif")
        val videoExt = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "mp3", "m3u8")

        val imageExtensions = imageExt + imageExt.map { it.uppercase() }
        val videoExtensions = videoExt + videoExt.map { it.uppercase() }

        // --- URL validation patterns ---

        // Android 9 seems to have an issue starting this regex.
        val noProtocolUrlValidator =
            try {
                Regex(
                    "(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}])*\\/?)(.*)",
                )
            } catch (e: Exception) {
                Regex(
                    "(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+)*\\/?)(.*)",
                )
            }

        val additionalUrlSchema =
            """^([A-Za-z0-9-_]+(\.[A-Za-z0-9-_]+)+)(:[0-9]+)?(/[^?#]*)?(\?[^#]*)?(#.*)?"""
                .toRegex(RegexOption.IGNORE_CASE)

        val HTTPRegex =
            "^((http|https)://)?([A-Za-z0-9-_]+(\\.[A-Za-z0-9-_]+)+)(:[0-9]+)?(/[^?#]*)?(\\?[^#]*)?(#.*)?"
                .toRegex(RegexOption.IGNORE_CASE)

        // --- Nostr / NIP-19 ---
        val acceptedNIP19schemes =
            listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed") +
                listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed").map {
                    it.uppercase()
                }

        // --- Hash tag / tag index ---
        val tagIndex = Regex("\\#\\[([0-9]+)\\](.*)")
        val hashTagsPattern: Regex =
            Regex("#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)(.*)", RegexOption.IGNORE_CASE)

        // Strips query string and fragment from a URL so the bare path extension can be compared.
        private fun stripUrlForExtensionCheck(fullUrl: String): String =
            if (fullUrl.contains("?")) {
                fullUrl.split("?")[0]
            } else if (fullUrl.contains("#")) {
                fullUrl.split("#")[0]
            } else {
                fullUrl
            }

        fun isImageOrVideoUrl(url: String): Boolean {
            val bare = stripUrlForExtensionCheck(url)
            return imageExtensions.any { bare.endsWith(it) } ||
                videoExtensions.any { bare.endsWith(it) }
        }

        fun isImageUrl(url: String): Boolean {
            val bare = stripUrlForExtensionCheck(url)
            return imageExtensions.any { bare.endsWith(it) }
        }

        fun isVideoUrl(url: String): Boolean {
            val bare = stripUrlForExtensionCheck(url)
            return videoExtensions.any { bare.endsWith(it) }
        }

        fun isValidURL(url: String?): Boolean =
            try {
                URL(url).toURI()
                true
            } catch (e: MalformedURLException) {
                false
            } catch (e: URISyntaxException) {
                false
            }

        fun parseImageOrVideo(fullUrl: String): BaseMediaContent {
            val bare = stripUrlForExtensionCheck(fullUrl)
            val isImage = imageExtensions.any { bare.endsWith(it) }
            val isVideo = videoExtensions.any { bare.endsWith(it) }

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
    }
}
