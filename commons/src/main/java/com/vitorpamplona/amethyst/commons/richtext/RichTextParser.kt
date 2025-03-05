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
package com.vitorpamplona.amethyst.commons.richtext

import android.util.Log
import android.util.Patterns
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentList
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern
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
            val removedParamsFromUrl = removeQueryParamsForExtensionComparison(fullUrl)
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

    private fun checkBase64(content: String): Boolean {
        val matcher = base64contentPattern.matcher(content)
        return matcher.find()
    }

    fun parseValidUrls(content: String): LinkedHashSet<String> {
        val urls = UrlDetector(content, UrlDetectorOptions.Default).detect()

        return urls.mapNotNullTo(LinkedHashSet(urls.size)) {
            if (it.originalUrl.contains("@")) {
                if (Patterns.EMAIL_ADDRESS.matcher(it.originalUrl).matches()) {
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

        val emojiMap = CustomEmoji.createEmojiMap(tags)

        val segments = findTextSegments(content, imagesForPager.keys, urlSet, emojiMap, tags)

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
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ImmutableList<ParagraphState> {
        val lines = content.split('\n')
        val paragraphSegments = ArrayList<ParagraphState>(lines.size)

        lines.forEach { paragraph ->
            var isDirty = false
            val isRTL = isArabic(paragraph)

            val wordList = paragraph.trimEnd().split(' ')
            val segments = ArrayList<Segment>(wordList.size)
            wordList.forEach { word ->
                val wordSegment = wordIdentifier(word, images, urls, emojis, tags)
                if (wordSegment !is RegularTextSegment) {
                    isDirty = true
                }
                segments.add(wordSegment)
            }

            val newSegments =
                if (isDirty) {
                    ParagraphState(segments.toPersistentList(), isRTL)
                } else {
                    ParagraphState(persistentListOf<Segment>(RegularTextSegment(paragraph)), isRTL)
                }

            paragraphSegments.add(newSegments)
        }

        return paragraphSegments.toImmutableList()
    }

    private fun isNumber(word: String) = numberPattern.matcher(word).matches()

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

    fun isDate(word: String): Boolean = shortDatePattern.matcher(word).matches() || longDatePattern.matcher(word).matches()

    private fun isArabic(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

    private fun wordIdentifier(
        word: String,
        images: Set<String>,
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        if (word.isEmpty()) return RegularTextSegment(word)

        if (word.startsWith("data:image/")) {
            if (checkBase64(word)) return Base64Segment(word)
        }

        if (images.contains(word)) return ImageSegment(word)

        if (urls.contains(word)) return LinkSegment(word)

        if (CustomEmoji.fastMightContainEmoji(word, emojis) && emojis.any { word.contains(it.key) }) return EmojiSegment(word)

        if (word.startsWith("lnbc", true)) return InvoiceSegment(word)

        if (word.startsWith("lnurl", true)) return WithdrawSegment(word)

        if (word.startsWith("cashuA", true) || word.startsWith("cashuB", true)) return CashuSegment(word)

        if (word.startsWith("#")) return parseHash(word, tags)

        if (EmojiCoder.isCoded(word)) return SecretEmoji(word)

        if (word.contains("@")) {
            if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) return EmailSegment(word)
        }

        if (startsWithNIP19Scheme(word)) return BechSegment(word)

        if (isPotentialPhoneNumber(word) && !isDate(word)) {
            if (Patterns.PHONE.matcher(word).matches()) return PhoneSegment(word)
        }

        val indexOfPeriod = word.indexOf(".")
        if (indexOfPeriod > 0 && indexOfPeriod < word.length - 1) { // periods cannot be the last one
            val schemelessMatcher = noProtocolUrlValidator.matcher(word)
            if (schemelessMatcher.find()) {
                val url = schemelessMatcher.group(1) // url
                val additionalChars = schemelessMatcher.group(4).ifEmpty { null } // additional chars
                val pattern =
                    """^([A-Za-z0-9-_]+(\.[A-Za-z0-9-_]+)+)(:[0-9]+)?(/[^?#]*)?(\?[^#]*)?(#.*)?"""
                        .toRegex(RegexOption.IGNORE_CASE)
                if (pattern.find(word) != null && url != null) {
                    return SchemelessUrlSegment(word, url, additionalChars)
                }
            }
        }

        return RegularTextSegment(word)
    }

    private fun parseHash(
        word: String,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        // First #[n]

        val matcher = tagIndex.matcher(word)
        try {
            if (matcher.find()) {
                val index = matcher.group(1)?.toInt()
                val suffix = matcher.group(2)

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
        val hashtagMatcher = hashTagsPattern.matcher(word)

        try {
            if (hashtagMatcher.find()) {
                val hashtag = hashtagMatcher.group(1)
                if (hashtag != null) {
                    return HashTagSegment(word, hashtag, hashtagMatcher.group(2).ifEmpty { null })
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
        }

        return RegularTextSegment(word)
    }

    companion object {
        val longDatePattern: Pattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")
        val shortDatePattern: Pattern = Pattern.compile("^\\d{2}-\\d{2}-\\d{2}$")
        val numberPattern: Pattern = Pattern.compile("^(-?[\\d.]+)([a-zA-Z%]*)$")

        // Android9 seems to have an issue starting this regex.
        val noProtocolUrlValidator =
            try {
                Pattern.compile(
                    "(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}])*\\/?)(.*)",
                )
            } catch (e: Exception) {
                Pattern.compile(
                    "(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+)*\\/?)(.*)",
                )
            }

        val HTTPRegex =
            "^((http|https)://)?([A-Za-z0-9-_]+(\\.[A-Za-z0-9-_]+)+)(:[0-9]+)?(/[^?#]*)?(\\?[^#]*)?(#.*)?"
                .toRegex(RegexOption.IGNORE_CASE)

        val imageExt = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg", "avif")
        val videoExt = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "mp3", "m3u8")

        val imageExtensions = imageExt + imageExt.map { it.uppercase() }
        val videoExtensions = videoExt + videoExt.map { it.uppercase() }

        val base64contentPattern = Pattern.compile("data:image/(${imageExtensions.joinToString(separator = "|") { it } });base64,([a-zA-Z0-9+/]+={0,2})")

        val tagIndex = Pattern.compile("\\#\\[([0-9]+)\\](.*)")
        val hashTagsPattern: Pattern =
            Pattern.compile("#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)(.*)", Pattern.CASE_INSENSITIVE)

        val acceptedNIP19schemes =
            listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed") +
                listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed").map {
                    it.uppercase()
                }

        private fun removeQueryParamsForExtensionComparison(fullUrl: String): String =
            if (fullUrl.contains("?")) {
                fullUrl.split("?")[0].lowercase()
            } else if (fullUrl.contains("#")) {
                fullUrl.split("#")[0].lowercase()
            } else {
                fullUrl.lowercase()
            }

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

        fun isUrlWithoutScheme(url: String) = noProtocolUrlValidator.matcher(url).matches()
    }
}
