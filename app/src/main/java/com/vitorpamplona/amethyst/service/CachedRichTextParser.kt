/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import android.util.Log
import android.util.LruCache
import android.util.Patterns
import androidx.compose.runtime.Immutable
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlContent
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlImage
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlVideo
import com.vitorpamplona.amethyst.ui.components.hashTagsPattern
import com.vitorpamplona.amethyst.ui.components.imageExtensions
import com.vitorpamplona.amethyst.ui.components.removeQueryParamsForExtensionComparison
import com.vitorpamplona.amethyst.ui.components.tagIndex
import com.vitorpamplona.amethyst.ui.components.videoExtensions
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import java.util.regex.Pattern

@Immutable
data class RichTextViewerState(
    val urlSet: ImmutableSet<String>,
    val imagesForPager: ImmutableMap<String, ZoomableUrlContent>,
    val imageList: ImmutableList<ZoomableUrlContent>,
    val customEmoji: ImmutableMap<String, String>,
    val paragraphs: ImmutableList<ParagraphState>,
)

data class ParagraphState(val words: ImmutableList<Segment>, val isRTL: Boolean)

object CachedRichTextParser {
    val richTextCache = LruCache<String, RichTextViewerState>(200)

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
    ): RichTextViewerState {
        return if (richTextCache[content] != null) {
            richTextCache[content]
        } else {
            val newUrls = RichTextParser().parseText(content, tags)
            richTextCache.put(content, newUrls)
            newUrls
        }
    }
}

// Group 1 = url, group 4 additional chars
// val noProtocolUrlValidator =
// Pattern.compile("(([\\w\\d-]+\\.)*[a-zA-Z][\\w-]+[\\.\\:]\\w+([\\/\\?\\=\\&\\#\\.]?[\\w-]+)*\\/?)(.*)")

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

class RichTextParser() {
    fun parseMediaUrl(fullUrl: String): ZoomableUrlContent? {
        val removedParamsFromUrl = removeQueryParamsForExtensionComparison(fullUrl)
        return if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
            val frags = Nip44UrlParser().parse(fullUrl)
            ZoomableUrlImage(
                url = fullUrl,
                description = frags["alt"],
                hash = frags["x"],
                blurhash = frags["blurhash"],
                dim = frags["dim"],
                contentWarning = frags["content-warning"],
            )
        } else if (videoExtensions.any { removedParamsFromUrl.endsWith(it) }) {
            val frags = Nip44UrlParser().parse(fullUrl)
            ZoomableUrlVideo(
                url = fullUrl,
                description = frags["alt"],
                hash = frags["x"],
                blurhash = frags["blurhash"],
                dim = frags["dim"],
                contentWarning = frags["content-warning"],
            )
        } else {
            null
        }
    }

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
    ): RichTextViewerState {
        val urls = UrlDetector(content, UrlDetectorOptions.Default).detect()

        val urlSet =
            urls.mapNotNullTo(LinkedHashSet(urls.size)) {
                // removes e-mails
                if (Patterns.EMAIL_ADDRESS.matcher(it.originalUrl).matches()) {
                    null
                } else if (isNumber(it.originalUrl)) {
                    null
                } else if (it.originalUrl.contains("。")) {
                    null
                } else {
                    if (HTTPRegex.matches(it.originalUrl)) {
                        it.originalUrl
                    } else {
                        null
                    }
                }
            }

        val imagesForPager =
            urlSet.mapNotNull { fullUrl -> parseMediaUrl(fullUrl) }.associateBy { it.url }
        val imageList = imagesForPager.values.toList()

        val emojiMap =
            tags.lists.filter { it.size > 2 && it[0] == "emoji" }.associate { ":${it[1]}:" to it[2] }

        val segments = findTextSegments(content, imagesForPager.keys, urlSet, emojiMap, tags)

        return RichTextViewerState(
            urlSet.toImmutableSet(),
            imagesForPager.toImmutableMap(),
            imageList.toImmutableList(),
            emojiMap.toImmutableMap(),
            segments,
        )
    }

    private fun findTextSegments(
        content: String,
        images: Set<String>,
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ImmutableList<ParagraphState> {
        var paragraphSegments = persistentListOf<ParagraphState>()

        content.split('\n').forEach { paragraph ->
            var segments = persistentListOf<Segment>()
            var isDirty = false

            val isRTL = isArabic(paragraph)

            val wordList = paragraph.trimEnd().split(' ')
            wordList.forEach { word ->
                val wordSegment = wordIdentifier(word, images, urls, emojis, tags)
                if (wordSegment !is RegularTextSegment) {
                    isDirty = true
                }
                segments = segments.add(wordSegment)
            }

            val newSegments =
                if (isDirty) {
                    ParagraphState(segments, isRTL)
                } else {
                    ParagraphState(persistentListOf<Segment>(RegularTextSegment(paragraph)), isRTL)
                }

            paragraphSegments = paragraphSegments.add(newSegments)
        }

        return paragraphSegments
    }

    fun isNumber(word: String): Boolean {
        return numberPattern.matcher(word).matches()
    }

    fun isDate(word: String): Boolean {
        return shortDatePattern.matcher(word).matches() || longDatePattern.matcher(word).matches()
    }

    private fun isArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
    }

    private fun wordIdentifier(
        word: String,
        images: Set<String>,
        urls: Set<String>,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        val emailMatcher = Patterns.EMAIL_ADDRESS.matcher(word)
        val phoneMatcher = Patterns.PHONE.matcher(word)
        val schemelessMatcher = noProtocolUrlValidator.matcher(word)

        return if (word.isEmpty()) {
            RegularTextSegment(word)
        } else if (images.contains(word)) {
            ImageSegment(word)
        } else if (urls.contains(word)) {
            LinkSegment(word)
        } else if (emojis.any { word.contains(it.key) }) {
            EmojiSegment(word)
        } else if (word.startsWith("lnbc", true)) {
            InvoiceSegment(word)
        } else if (word.startsWith("lnurl", true)) {
            WithdrawSegment(word)
        } else if (word.startsWith("cashuA", true)) {
            CashuSegment(word)
        } else if (emailMatcher.matches()) {
            EmailSegment(word)
        } else if (word.length in 7..14 && !isDate(word) && phoneMatcher.matches()) {
            PhoneSegment(word)
        } else if (startsWithNIP19Scheme(word)) {
            BechSegment(word)
        } else if (word.startsWith("#")) {
            parseHash(word, tags)
        } else if (word.contains(".") && schemelessMatcher.find()) {
            val url = schemelessMatcher.group(1) // url
            val additionalChars = schemelessMatcher.group(4) // additional chars
            val pattern =
                """^([A-Za-z0-9-_]+(\.[A-Za-z0-9-_]+)+)(:[0-9]+)?(/[^?#]*)?(\?[^#]*)?(#.*)?"""
                    .toRegex(RegexOption.IGNORE_CASE)
            if (pattern.find(word) != null) {
                SchemelessUrlSegment(word, url, additionalChars)
            } else {
                RegularTextSegment(word)
            }
        } else {
            RegularTextSegment(word)
        }
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
                    return HashTagSegment(word, hashtag, hashtagMatcher.group(2))
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
    }
}

@Immutable open class Segment(val segmentText: String)

@Immutable class ImageSegment(segment: String) : Segment(segment)

@Immutable class LinkSegment(segment: String) : Segment(segment)

@Immutable class EmojiSegment(segment: String) : Segment(segment)

@Immutable class InvoiceSegment(segment: String) : Segment(segment)

@Immutable class WithdrawSegment(segment: String) : Segment(segment)

@Immutable class CashuSegment(segment: String) : Segment(segment)

@Immutable class EmailSegment(segment: String) : Segment(segment)

@Immutable class PhoneSegment(segment: String) : Segment(segment)

@Immutable class BechSegment(segment: String) : Segment(segment)

@Immutable
open class HashIndexSegment(segment: String, val hex: String, val extras: String?) :
    Segment(segment)

@Immutable
class HashIndexUserSegment(segment: String, hex: String, extras: String?) :
    HashIndexSegment(segment, hex, extras)

@Immutable
class HashIndexEventSegment(segment: String, hex: String, extras: String?) :
    HashIndexSegment(segment, hex, extras)

@Immutable
class HashTagSegment(segment: String, val hashtag: String, val extras: String?) : Segment(segment)

@Immutable
class SchemelessUrlSegment(segment: String, val url: String, val extras: String?) :
    Segment(segment)

@Immutable class RegularTextSegment(segment: String) : Segment(segment)

fun startsWithNIP19Scheme(word: String): Boolean {
    val cleaned = word.lowercase().removePrefix("@").removePrefix("nostr:").removePrefix("@")

    return listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1").any { cleaned.startsWith(it) }
}
