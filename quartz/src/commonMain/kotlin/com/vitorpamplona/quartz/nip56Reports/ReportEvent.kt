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
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip32Labeling.labelTags
import com.vitorpamplona.quartz.nip56Reports.tags.DefaultReportTag
import com.vitorpamplona.quartz.nip56Reports.tags.HashSha256Tag
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

    /**
     * The report's default type as written, found the way [defaultReportType] finds the type:
     * the legacy `report` tag, else the first `p`/`e`/`a` that writes one.
     */
    private fun defaultReportRawType(): String? =
        tags.firstNotNullOfOrNull(DefaultReportTag::parseRaw)
            ?: tags.firstNotNullOfOrNull {
                ReportedAuthorTag.parse(it)?.rawType
                    ?: ReportedEventTag.parse(it)?.rawType
                    ?: ReportedAddressTag.parse(it)?.rawType
            }

    /**
     * Whether the report names any content: an event, an address or a blob. One that names none
     * is a complaint about the person its `p` names; otherwise that `p` is the content's author.
     */
    fun isAboutContent() = tags.fastAny { ReportedEventTag.parseId(it) != null || ReportedAddressTag.parseAddressId(it) != null || HashSha256Tag.parse(it) != null }

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
     * NIP-56. What a report is ABOUT decides the relation of its `p` ([isAboutContent]): when the
     * report names no event, address or blob it is a complaint about the person
     * (`REPORTED_USER`); otherwise the `p` is the reported content's author (`REPORTED_AUTHOR`).
     * The split is by the presence of `e`/`a`/`x`, never by whether the `p` writes its own type:
     * Quartz's own [build] writes the type on both. `e`/`a`/`x` are the `REPORTED` content (`x`: a
     * blob hash).
     *
     * Every one of them carries the tag's [BaseReportTag.linkProps]: `report`, the category as
     * Quartz reads it (the tag's own type, else the report's default, as a [ReportType] code), and
     * `report_raw`, the type as written (trimmed and lowercased; clients invent types that fold
     * into `other`), else the report's default as written.
     */
    override fun links(): List<Link<*>> =
        links {
            val defaultType = defaultReportType()
            val defaultRaw = defaultReportRawType()
            val personRelation = if (isAboutContent()) Relation.REPORTED_AUTHOR else Relation.REPORTED_USER

            each(tags, { ReportedEventTag.parse(it, defaultType, defaultRaw) }) { event(Relation.REPORTED, it, ReportedEventTag.TAG_NAME, it.linkProps()) }
            each(tags, { ReportedAuthorTag.parse(it, defaultType, defaultRaw) }) { user(personRelation, it, ReportedAuthorTag.TAG_NAME, it.linkProps()) }
            each(tags, { ReportedAddressTag.parse(it, defaultType, defaultRaw) }) { address(Relation.REPORTED, it, ReportedAddressTag.TAG_NAME, it.linkProps()) }
            each(tags, { HashSha256Tag.parse(it, defaultType, defaultRaw) }) { tag(Relation.REPORTED, HashSha256Tag.TAG_NAME, it.hash, props = it.linkProps()) }

            labelTags(tags)
        }

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
