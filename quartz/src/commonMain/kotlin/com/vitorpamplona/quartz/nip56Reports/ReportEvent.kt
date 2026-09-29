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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.quartz.nip56Reports

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip56Reports.tags.DefaultReportTag
import com.vitorpamplona.quartz.nip56Reports.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportTagLayout
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAddressTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAuthorTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedEventTag
import com.vitorpamplona.quartz.utils.TimeUtils

// NIP 56 event.
@Immutable
class ReportEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider,
    LinkProvider {
    override fun pubKeyHints() = tags.mapNotNull(ReportedAuthorTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(ReportedAuthorTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ReportedEventTag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ReportedEventTag::parseId)

    override fun addressHints() = tags.mapNotNull(ReportedAddressTag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ReportedAddressTag::parseAddressId)

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var defaultType: ReportType? = null

    private fun defaultReportTypes() = tags.mapNotNull(DefaultReportTag::parse)

    private fun defaultReportType(): ReportType {
        defaultType?.let { return it }

        // search for any type in any tag.
        val reportType =
            defaultReportTypes().firstOrNull()
                ?: tags.firstNotNullOfOrNull {
                    ReportedAuthorTag.parse(it)?.type
                        ?: ReportedEventTag.parse(it)?.type
                        ?: ReportedAddressTag.parse(it)?.type
                } ?: ReportType.SPAM

        defaultType = reportType
        return reportType
    }

    fun reportedPost() = tags.mapNotNull { ReportedEventTag.parse(it, defaultReportType()) }

    fun reportedAddresses() = tags.mapNotNull { ReportedAddressTag.parse(it, defaultReportType()) }

    fun reportedAuthor() = tags.mapNotNull { ReportedAuthorTag.parse(it, defaultReportType()) }

    /**
     * Only the authors whose own `p` tag carries a report type, without the event-level fallback
     * [reportedAuthor] applies. Separates an author this report is actually about from one it
     * merely `p`-tags as a mention of an otherwise event-scoped report.
     */
    fun reportedAuthorsWithOwnType() =
        tags.mapNotNull { tag ->
            ReportedAuthorTag.parse(tag)?.takeIf { it.type != null }
        }

    /**
     * NIP-56. What a report is ABOUT decides the relation of its `p`: when the report names no
     * event, address or blob it is a complaint about the person (`REPORTED_USER`); otherwise the
     * `p` is the reported content's author (`REPORTED_AUTHOR`). The split is by the presence of
     * `e`/`a`/`x`, never by whether the `p` writes its own type: Quartz's own [build] writes the
     * type on both. `e`/`a`/`x` are the `REPORTED` content (`x`: a blob hash).
     *
     * Every one of them carries `report`, the category as Quartz reads it (the tag's own type,
     * else the report's default, as a [ReportType] code), and `report_raw`, the type as written
     * (trimmed and lowercased; clients invent types that fold into `other`).
     */
    override fun links(): List<Link> {
        val defaultType = defaultReportType()
        var defaultRaw: String? = null
        var ownDefaultRaw: String? = null
        var aboutContent = false
        tags.fastForEach { tag ->
            if (tag.size < 2) return@fastForEach
            when (tag[0]) {
                DefaultReportTag.TAG_NAME -> if (defaultRaw == null) defaultRaw = rawReportType(tag[1])
                "e" -> if (LinkBuilder.normalizedHex(tag[1]) != null) aboutContent = true
                "a" -> if (LinkBuilder.normalizedAddress(tag[1]) != null) aboutContent = true
                "x" -> if (HashSha256Tag.parse(tag) != null) aboutContent = true
            }
            if (ownDefaultRaw == null && (tag[0] == "p" || tag[0] == "e" || tag[0] == "a")) ownDefaultRaw = ownRawReportType(tag)
        }
        val fallbackRaw = defaultRaw ?: ownDefaultRaw

        fun props(
            type: ReportType?,
            raw: String?,
        ): Map<String, Any>? {
            val text = raw ?: fallbackRaw
            return buildMap {
                if (type != null) put("report", type.code)
                if (text != null) put("report_raw", text)
            }.ifEmpty { null }
        }

        return links {
            tags.fastForEach { tag ->
                if (tag.size < 2) return@fastForEach
                when (tag[0]) {
                    "p" -> {
                        val relation = if (aboutContent) Relation.REPORTED_AUTHOR else Relation.REPORTED_USER
                        user(relation, tag[1], "p", props(ReportedAuthorTag.parse(tag, defaultType)?.type, ownRawReportType(tag)))
                    }

                    "e" -> event(Relation.REPORTED, tag[1], "e", props(ReportedEventTag.parse(tag, defaultType)?.type, ownRawReportType(tag)))

                    "a" -> address(Relation.REPORTED, tag[1], "a", props(ReportedAddressTag.parse(tag, defaultType)?.type, ownRawReportType(tag)))

                    "x" -> {
                        val hash = HashSha256Tag.parse(tag, defaultType) ?: return@fastForEach
                        tag(Relation.REPORTED, "x", hash.hash, props = props(hash.type, tag.getOrNull(2)?.let(::rawReportType)))
                    }

                    "l", "L" -> tag(Relation.TAG, tag[0], tag[1])
                }
            }
        }
    }

    /** The type a `p`/`e`/`a` writes itself, by [ReportTagLayout]: slot 3 when slot 2 is blank or a relay hint, else slot 2. */
    private fun ownRawReportType(tag: Array<String>): String? {
        if (tag.size < 3) return null
        val slot = if (tag[2].isBlank() || ReportTagLayout.relayHint(tag) != null) 3 else 2
        return tag.getOrNull(slot)?.let(::rawReportType)
    }

    private fun rawReportType(value: String) = value.trim().lowercase().ifEmpty { null }

    companion object {
        const val KIND = 1984

        fun build(
            reportedPost: Event,
            type: ReportType,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate(KIND, comment, createdAt) {
            event(reportedPost.id, type)
            user(reportedPost.pubKey, type)

            if (reportedPost is AddressableEvent) {
                address(reportedPost.address(), type)
            }
        }

        fun build(
            reportedUser: HexKey,
            type: ReportType,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate(KIND, comment, createdAt) {
            user(reportedUser, type)
        }
    }
}
