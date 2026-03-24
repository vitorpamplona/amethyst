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
import com.vitorpamplona.amethyst.commons.richtext.mimeTypeMap
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
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
            isVideo = contentType.startsWith("video/") || contentType.startsWith("audio/")
        } else if (fullUrl.startsWith("data:")) {
            isImage = fullUrl.startsWith("data:image/")
            isVideo = fullUrl.startsWith("data:video/") || fullUrl.startsWith("data:audio/")
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

    fun fixMissingSpaces(
        input: String,
        urlList: Set<String>,
    ): String {
        if (urlList.isEmpty()) return input

        // Escape and join words: (word1|word2)
        val wordsPattern = urlList.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        // Regex breakdown:
        // ([^ ])?          -> Group 1: Optional character that is NOT a space or new line (Prefix)
        // ($wordsPattern)  -> Group 2: One of your target words
        // ([^ ])?          -> Group 3: Optional character that is NOT a space or new line (Suffix)
        val regex = Regex("([^ \n])?($wordsPattern)([^ \n])?")

        return regex.replace(input) { match ->
            val prefix = match.groups[1]?.value ?: ""
            val word = match.groups[2]?.value ?: ""
            val suffix = match.groups[3]?.value ?: ""

            val result = StringBuilder()

            // Add prefix + space if the prefix exists
            if (prefix.isNotEmpty()) {
                result.append(prefix)
                result.append(" ")
            }

            result.append(word)

            // Add space + suffix if the suffix exists
            if (suffix.isNotEmpty()) {
                result.append(" ")
                result.append(suffix)
            }

            result.toString()
        }
    }

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
    ): RichTextViewerState {
        val imetas = tags.lists.imetasByUrl()
        val urlSet = UrlParser().parseValidUrls(content)

        val mediaContents =
            urlSet.withScheme.mapNotNull { fullUrl ->
                createMediaContent(fullUrl, imetas, content, callbackUri)
            } +
                urlSet.withoutScheme.mapNotNull { fullUrl ->
                    createMediaContent(fullUrl, imetas, content, callbackUri)
                }

        val mediaForPager = mediaContents.associateBy { it.url }

        val imageUrls = mediaForPager.filterValues { it is MediaUrlImage }.keys
        val videoUrls = mediaForPager.filterValues { it is MediaUrlVideo }.keys

        val emojiMap = CustomEmoji.createEmojiMap(tags.lists)

        val allUrls = urlSet.withScheme + urlSet.withoutScheme + urlSet.emails + urlSet.bech32s + urlSet.relayUrls + urlSet.blossomUris

        val newContent = fixMissingSpaces(content, allUrls)

        val segments = findTextSegments(newContent, imageUrls, videoUrls, urlSet, emojiMap, tags)

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
        urls: Urls,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ImmutableList<ParagraphState> {
        val lines = content.split('\n')
        val paragraphSegments = ArrayList<ParagraphState>(lines.size)

        lines.forEach { paragraph ->
            val isRTL = isArabic(paragraph)

            val wordList = paragraph.trimEnd().split(' ')

            val segments = ArrayList<Segment>(wordList.size)
            wordList.forEach { word ->
                segments.add(wordIdentifier(word, images, videos, urls, emojis, tags))
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

        // Namecoin identifiers must be checked BEFORE schemeless URLs,
        // because .bit domains look like URLs to the URL parser but should
        // resolve via the Namecoin blockchain, not open a browser.
        val trimmedWord = word.trimEnd('.', ',', '!', '?', ')', ']')
        if (NamecoinNameResolver.isNamecoinIdentifier(trimmedWord)) return NamecoinSegment(word)

        if (urls.withoutScheme.contains(word)) return SchemelessUrlSegment(word)

        if (urls.withScheme.contains(word)) return LinkSegment(word)

        if (urls.emails.contains(word)) return EmailSegment(word)

        if (urls.bech32s.contains(word)) return BechSegment(word)

        if (urls.relayUrls.contains(word)) return RelayUrlSegment(word)

        if (urls.blossomUris.contains(word)) return BlossomUriSegment(word)

        if (startsWithNIP19Scheme(word)) return BechSegment(word)

        if (CustomEmoji.fastMightContainEmoji(word, emojis) && emojis.any { word.contains(it.key) }) return EmojiSegment(word)

        if (word.startsWith("lnbc", true)) return InvoiceSegment(word)

        if (word.startsWith("lnurl", true)) return WithdrawSegment(word)

        if (word.startsWith("cashuA", true) || word.startsWith("cashuB", true)) return CashuSegment(word)

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
        val videoExt = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "mp3", "m3u8", "ogg", "wav", "flac", "aac", "opus", "m4a")

        val imageExtensions = imageExt + imageExt.map { it.uppercase() }
        val videoExtensions = videoExt + videoExt.map { it.uppercase() }

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

        fun isUrlWithoutScheme(url: String) = noProtocolUrlValidator.matches(url)
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
        "aac" to "audio/aac",
        "flac" to "audio/flac",
    )
