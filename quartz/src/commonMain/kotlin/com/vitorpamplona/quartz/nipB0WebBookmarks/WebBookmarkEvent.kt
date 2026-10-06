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
package com.vitorpamplona.quartz.nipB0WebBookmarks

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class WebBookmarkEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    SearchableEvent {
    // The `t` tags are the labels the user filed the bookmark under.
    override fun indexableContent() = (listOfNotNull(title(), description()) + hashtags()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(description())) return
        // Walks the tags in place: this runs per event per search keystroke, and hashtags()
        // would build a list only to discard it.
        for (tag in tags) {
            val hashtag = HashtagTag.parse(tag) ?: continue
            if (!visitor.visit(hashtag)) return
        }
    }

    /**
     * The bookmarked URI. NIP-B0 only drops the scheme for `https`, so a d tag without a scheme
     * is an https URL and anything else (`http://`, `gemini://`, `magnet:`...) is already complete.
     */
    fun url(): String = dTagToUrl(dTag())

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun publishedAt() = tags.firstNotNullOfOrNull(PublishedAtTag::parse)

    fun hashtags() = tags.hashtags()

    fun description() = content

    companion object {
        const val KIND = 39701

        private const val HTTPS_PREFIX = "https://"

        // RFC 3986: scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) followed by ":"
        private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):(.*)$")

        // What follows "host:" when the colon introduces a port rather than ending a scheme.
        private val PORT = Regex("^[0-9]{1,5}([/?#].*)?$")

        /**
         * True when [uri] starts with a scheme (`http://`, `mailto:`, `magnet:`) rather than a
         * `host[:port]` of a scheme-less https d tag. `://` always means a scheme; otherwise a
         * dotted or `localhost` prefix, or a numeric port after the colon, means a host.
         */
        fun hasScheme(uri: String): Boolean {
            val match = SCHEME.find(uri) ?: return false
            val candidate = match.groupValues[1]
            val rest = match.groupValues[2]
            if (rest.startsWith("//")) return true
            if (candidate.contains('.') || candidate.equals("localhost", ignoreCase = true)) return false
            return !PORT.matches(rest)
        }

        /**
         * NIP-B0 d tag: the URI itself, except that for `https` everything before the hostname
         * (scheme, `//` and any userinfo) is omitted. Other schemes, `http` included, are kept.
         *
         * The spec says nothing about trailing slashes or case; this keeps the long-standing
         * trailing-slash trim (so re-saving an old bookmark lands on the same address) and does
         * not change case.
         */
        fun urlToDTag(url: String): String {
            val trimmed = url.trim()
            val uri =
                if (trimmed.startsWith(HTTPS_PREFIX, ignoreCase = true)) {
                    val afterScheme = trimmed.substring(HTTPS_PREFIX.length)
                    val authorityEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) afterScheme.length else it }
                    val userInfoEnd = afterScheme.lastIndexOf('@', authorityEnd - 1)
                    if (userInfoEnd >= 0) afterScheme.substring(userInfoEnd + 1) else afterScheme
                } else {
                    trimmed
                }
            return uri.trimEnd('/')
        }

        fun dTagToUrl(dTag: String): String =
            when {
                dTag.isEmpty() -> ""
                hasScheme(dTag) -> dTag
                else -> HTTPS_PREFIX + dTag
            }

        /**
         * @param firstPublishedAt when the bookmark was first published; pass the original value when
         * editing so it survives the replacement.
         */
        fun build(
            url: String,
            bookmarkTitle: String?,
            description: String,
            tags: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            firstPublishedAt: Long = createdAt,
            initializer: TagArrayBuilder<WebBookmarkEvent>.() -> Unit = {},
        ) = eventTemplate<WebBookmarkEvent>(KIND, description, createdAt) {
            dTag(urlToDTag(url))
            bookmarkTitle?.let { title(it) }
            publishedAt(firstPublishedAt)
            hashtags(tags)
            initializer()
        }
    }
}
