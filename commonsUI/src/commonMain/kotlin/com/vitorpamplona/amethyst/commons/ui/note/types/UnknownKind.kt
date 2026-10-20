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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.rendering.unknown.BlobEncoding
import com.vitorpamplona.amethyst.commons.rendering.unknown.ContentShape
import com.vitorpamplona.amethyst.commons.rendering.unknown.DigestMedia
import com.vitorpamplona.amethyst.commons.rendering.unknown.UnknownEventDigest
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_client
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_content
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_base64
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_hex
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_opaque
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encrypted
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_ends
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_expires
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_hide_tags
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_identifier
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_json_items
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_kinds
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_links
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_location
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_more_tags
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_published
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_relays
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_show_tags
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_starts
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_tags
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_title
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_unsupported
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_unsupported_line
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.Hex

/*
 * PROTOTYPES: the fallback for events of a kind the app has no renderer for. Until now they fell
 * into the text path, which prints whatever the content holds (JSON, ciphertext, base64) and drops
 * every tag. All three read the same [UnknownEventDigest] (commons/rendering/unknown) and differ
 * only in how much they show and how:
 *
 * - [UnknownKindStyle.Annotated]: still a post. A muted "Recipe · not supported yet" line, then
 *   title, summary, picture and the content when it is prose, the hashtags, people and the first
 *   reference. The rest of the tags fold away under "N more tags".
 * - [UnknownKindStyle.Card]: a kind card like the other record kinds. The title is the event's own
 *   title (or the kind's name), the pill says "Unsupported", and the facts line up in a table:
 *   identifier, dates, location, links, JSON fields when the content is a JSON object.
 * - [UnknownKindStyle.Inspector]: every tag in its order, by its own name, each value drawn by
 *   what it is (a person chip for `p`, a link for `e`/`a`/URLs, a date for timestamps). For
 *   developers and the curious; nothing is hidden or interpreted away.
 */
enum class UnknownKindStyle { Annotated, Card, Inspector }

/** Which prototype the note rows draw. A CompositionLocal only so the render test can switch it. */
val LocalUnknownKindStyle = staticCompositionLocalOf { UnknownKindStyle.Card }

@Composable
fun RenderUnknownKind(
    note: Note,
    makeItShort: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    style: UnknownKindStyle = LocalUnknownKindStyle.current,
) {
    val event = note.event ?: return
    val digest = remember(event) { UnknownEventDigest.of(event) }
    when (style) {
        UnknownKindStyle.Annotated -> UnknownKindAnnotated(note, digest, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)
        UnknownKindStyle.Card -> UnknownKindCard(note, digest, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)
        UnknownKindStyle.Inspector -> UnknownKindInspector(note, digest, makeItShort, backgroundColor, accountViewModel, nav)
    }
}

// ---------------------------------------------------------------------------------------------
// A: Annotated text
// ---------------------------------------------------------------------------------------------

@Composable
private fun UnknownKindAnnotated(
    note: Note,
    digest: UnknownEventDigest,
    makeItShort: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                symbol = MaterialSymbols.Extension,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringRes(Res.string.unknown_kind_unsupported_line, unknownKindLabel(digest)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        digest.title?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        digest.summary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (makeItShort) 2 else 6, overflow = TextOverflow.Ellipsis)
        }

        if (!makeItShort) digest.media.firstOrNull()?.let { UnknownKindImage(it, accountViewModel) }

        val prose = digest.readableText
        when {
            digest.content is ContentShape.Prose -> {
                if (makeItShort) {
                    Text(prose ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    KindCardText(note, prose, backgroundColor, accountViewModel, nav, quotesLeft)
                }
            }

            // The content is for software: the author's own description stands in for it.
            prose != null && prose != digest.title && prose != digest.summary -> {
                Text(prose, style = MaterialTheme.typography.bodyMedium, maxLines = if (makeItShort) 2 else 6, overflow = TextOverflow.Ellipsis)
                UnknownContentLine(digest.content)
            }

            else -> UnknownContentLine(digest.content)
        }

        if (makeItShort) return@Column

        KindCardChips(digest.uncitedHashtags.map { "#$it" })
        KindCardUsers(digest.people.map { it.pubKey }, accountViewModel, nav)
        UnknownKindFirstReference(digest, quotesLeft, backgroundColor, accountViewModel, nav)

        val rest = remember(digest) { digest.otherTags.size + digest.links.size + digest.relays.size + (digest.events.size + digest.addresses.size - 1).coerceAtLeast(0) }
        UnknownKindFold(
            count = rest,
            closedLabel = { pluralStringRes(Res.plurals.unknown_kind_more_tags, it, it) },
        ) {
            UnknownKindFacts(digest)
            digest.otherTags.forEach { KindCardField(it.name, it.values.joinToString("  ·  "), monospace = true) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B: Kind card
// ---------------------------------------------------------------------------------------------

@Composable
private fun UnknownKindCard(
    note: Note,
    digest: UnknownEventDigest,
    makeItShort: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val kindLabel = unknownKindLabel(digest)
    KindCard(
        icon = MaterialSymbols.Extension,
        title = digest.title ?: kindLabel,
        subtitle = if (digest.title != null) kindLabel else digest.alt?.takeIf { digest.content !is ContentShape.Prose },
        tone = KindTone.Neutral,
        status = stringRes(Res.string.unknown_kind_unsupported),
    ) {
        if (!makeItShort) digest.media.firstOrNull()?.let { UnknownKindImage(it, accountViewModel) }
        digest.summary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = if (makeItShort) 2 else 5, overflow = TextOverflow.Ellipsis)
        }

        when (val content = digest.content) {
            is ContentShape.Prose -> {
                if (makeItShort) {
                    Text(content.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    KindCardProse(null, note, content.text, backgroundColor, accountViewModel, nav)
                }
            }

            is ContentShape.Json -> {
                if (!makeItShort) {
                    if (content.isArray || content.fields.isEmpty()) {
                        UnknownContentLine(content)
                        KindCardCode(content.pretty, maxLines = 6)
                    } else {
                        content.fields.take(MAX_JSON_FIELDS).forEach { KindCardField(readableToken(it.key), it.value, monospace = it.nested) }
                        KindCardMore(content.fields.size - MAX_JSON_FIELDS)
                    }
                }
            }

            else -> {
                // With no title, the alt is already the subtitle.
                if (digest.title != null) digest.alt?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                UnknownContentLine(content)
            }
        }

        if (makeItShort) return@KindCard

        UnknownKindFacts(digest)
        KindCardChips(digest.uncitedHashtags.map { "#$it" })
        KindCardUsers(digest.people.map { it.pubKey }, accountViewModel, nav)
        UnknownKindFirstReference(digest, quotesLeft, backgroundColor, accountViewModel, nav)

        UnknownKindFold(
            count = digest.otherTags.size,
            closedLabel = { pluralStringRes(Res.plurals.unknown_kind_show_tags, it, it) },
        ) {
            digest.otherTags.forEach { KindCardField(it.name, it.values.joinToString("  ·  "), monospace = true) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// C: Inspector
// ---------------------------------------------------------------------------------------------

@Composable
private fun UnknownKindInspector(
    note: Note,
    digest: UnknownEventDigest,
    makeItShort: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tags = remember(note.event) { note.event?.tags ?: emptyArray() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoChip("kind ${digest.kind}")
            Text(
                text = digest.kindName?.let { kindName(digest.kind) } ?: stringRes(Res.string.unknown_kind_unsupported),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        digest.alt?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }

        when (val content = digest.content) {
            ContentShape.Empty -> {}

            is ContentShape.Prose -> {
                KindCardSection(stringRes(Res.string.unknown_kind_content))
                if (makeItShort) {
                    Text(content.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        KindCardText(note, content.text, backgroundColor, accountViewModel, nav)
                    }
                }
            }

            is ContentShape.Json -> {
                KindCardSection(stringRes(Res.string.unknown_kind_content) + "  ·  JSON")
                KindCardCode(content.pretty, maxLines = if (makeItShort) 3 else 10)
            }

            else -> {
                KindCardSection(stringRes(Res.string.unknown_kind_content))
                UnknownContentLine(content)
                if (!makeItShort && content is ContentShape.Encoded) KindCardCode(note.event?.content ?: "", maxLines = 2)
            }
        }

        if (makeItShort || tags.isEmpty()) return@Column

        KindCardSection(stringRes(Res.string.unknown_kind_tags) + "  ·  ${tags.size}")
        var expanded by rememberSaveable(note.idHex) { mutableStateOf(false) }
        val shown = if (expanded || tags.size <= INSPECTOR_COLLAPSED_TAGS + 1) tags.toList() else tags.take(INSPECTOR_COLLAPSED_TAGS)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            shown.forEach { InspectorTagRow(it, accountViewModel, nav) }
        }
        if (tags.size > INSPECTOR_COLLAPSED_TAGS + 1) {
            FoldToggle(
                open = expanded,
                label = if (expanded) stringRes(Res.string.unknown_kind_hide_tags) else pluralStringRes(Res.plurals.unknown_kind_more_tags, tags.size - shown.size, tags.size - shown.size),
                onClick = { expanded = !expanded },
            )
        }
    }
}

/** One tag as a row: its name in a code chip, then each value drawn by what it is. */
@Composable
private fun InspectorTagRow(
    tag: Array<String>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tag.isEmpty()) return
    val name = tag[0]
    Row(verticalAlignment = Alignment.Top) {
        Row(Modifier.width(96.dp).padding(end = 8.dp)) { MonoChip(name) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val value = tag.getOrNull(1) ?: ""
            when {
                name == "p" && Hex.isHex64(value) -> {
                    KindCardPerson(value, accountViewModel, nav)
                    InspectorExtras(tag, from = 2)
                }

                (name == "e" || name == "q") && Hex.isHex64(value) -> {
                    InspectorLink(value.take(8) + "…" + value.takeLast(4)) { nav.nav(Route.EventRedirect(value)) }
                    InspectorExtras(tag, from = 2)
                }

                (name == "a" || name == "q") && Address.parse(value) != null -> {
                    val address = remember(value) { Address.parse(value)!! }
                    InspectorLink("${kindName(address.kind)} · ${address.dTag.ifEmpty { address.pubKeyHex.take(8) }}") {
                        routeFor(LocalCache.getOrCreateAddressableNote(address), accountViewModel.account)?.let { nav.nav(it) }
                    }
                    InspectorExtras(tag, from = 2)
                }

                name == "imeta" -> tag.drop(1).forEach { InspectorValue(it) }

                name in TIMESTAMP_TAGS && value.toLongOrNull() != null -> {
                    InspectorValue(formatDateTime(value.toLong() * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT))
                    InspectorExtras(tag, from = 2)
                }

                name == "k" && value.toIntOrNull() != null -> InspectorValue("${kindName(value.toInt())} ($value)")

                else -> tag.drop(1).filter { it.isNotEmpty() }.forEach { InspectorValue(it) }
            }
        }
    }
}

@Composable
private fun InspectorExtras(
    tag: Array<String>,
    from: Int,
) {
    val rest = tag.drop(from).filter { it.isNotBlank() }
    if (rest.isNotEmpty()) {
        Text(
            text = rest.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InspectorValue(value: String) {
    if (value.startsWith("https://") || value.startsWith("http://") || value.startsWith("wss://")) {
        ClickableUrl(url = value, style = MaterialTheme.typography.bodySmall)
    } else {
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InspectorLink(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MonoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

/** "Recipe" when quartz knows the kind's name, "Kind 31337" when it doesn't. */
@Composable
private fun unknownKindLabel(digest: UnknownEventDigest): String = if (digest.kindName != null) kindName(digest.kind) else stringRes(Res.string.unknown_kind_title, digest.kind)

/** One quiet line saying what the content is when it isn't words: ciphertext, a blob, a JSON list. */
@Composable
private fun UnknownContentLine(content: ContentShape) {
    val text =
        when (content) {
            is ContentShape.Encrypted -> stringRes(Res.string.unknown_kind_encrypted, content.scheme.name.let { it.substring(0, 3) + "-" + it.substring(3) })
            is ContentShape.Encoded ->
                when (content.encoding) {
                    BlobEncoding.HEX -> stringRes(Res.string.unknown_kind_encoded_hex, content.length)
                    BlobEncoding.BASE64 -> stringRes(Res.string.unknown_kind_encoded_base64, content.length)
                    BlobEncoding.OPAQUE -> stringRes(Res.string.unknown_kind_encoded_opaque, content.length)
                }
            is ContentShape.Json -> if (content.isArray) pluralStringRes(Res.plurals.unknown_kind_json_items, content.size, content.size) else null
            else -> null
        } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            symbol = if (content is ContentShape.Encrypted) MaterialSymbols.Lock else MaterialSymbols.Code,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The facts the digest found, as a table: only the rows that have a value draw. */
@Composable
private fun UnknownKindFacts(digest: UnknownEventDigest) {
    KindCardField(stringRes(Res.string.unknown_kind_identifier), digest.identifier, monospace = true, maxLines = 1)
    KindCardField(stringRes(Res.string.unknown_kind_published), digest.publishedAt?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_starts), digest.startsAt?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_ends), digest.endsAt?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_expires), digest.expiration?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_location), digest.location ?: digest.geohash)
    if (digest.kinds.isNotEmpty()) KindCardField(stringRes(Res.string.unknown_kind_kinds), digest.kinds.map { kindName(it) }.joinToString(", "))
    KindCardField(stringRes(Res.string.unknown_kind_client), digest.client)
    if (digest.links.isNotEmpty()) {
        KindCardSection(stringRes(Res.string.unknown_kind_links))
        digest.links.take(3).forEach { ClickableUrl(url = it, style = MaterialTheme.typography.bodySmall) }
        KindCardMore(digest.links.size - 3)
    }
    if (digest.relays.isNotEmpty()) KindCardSectionChips(stringRes(Res.string.unknown_kind_relays), digest.relays.map { it.removePrefix("wss://").removeSuffix("/") }, max = 4)
}

/** The first event the record points at, quoted; the card is usually about it. */
@Composable
private fun UnknownKindFirstReference(
    digest: UnknownEventDigest,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = digest.events.firstOrNull()
    if (event != null) {
        KindCardQuoteEvent(event.id, quotesLeft, backgroundColor, accountViewModel, nav)
        return
    }
    digest.addresses.firstOrNull()?.let { KindCardQuoteAddress(it.address, quotesLeft, backgroundColor, accountViewModel, nav) }
}

@Composable
private fun UnknownKindImage(
    media: DigestMedia,
    accountViewModel: AccountViewModel,
) {
    MyAsyncImage(
        imageUrl = media.url,
        contentDescription = media.alt,
        contentScale = ContentScale.Crop,
        mainImageModifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)),
        loadedImageModifier = Modifier,
        accountViewModel = accountViewModel,
        onLoadingBackground = null,
        onError = null,
    )
}

/** A fold: "N more tags" that opens onto [content]. Nothing when [count] is 0. */
@Composable
private fun UnknownKindFold(
    count: Int,
    closedLabel: @Composable (Int) -> String,
    content: @Composable () -> Unit,
) {
    if (count <= 0) return
    var open by rememberSaveable { mutableStateOf(false) }
    FoldToggle(open, if (open) stringRes(Res.string.unknown_kind_hide_tags) else closedLabel(count)) { open = !open }
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun FoldToggle(
    open: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 2.dp, horizontal = 2.dp),
    ) {
        Icon(
            symbol = if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSeconds(epochSeconds: Long): String = formatDateTime(epochSeconds * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)

private const val MAX_JSON_FIELDS = 8
private const val INSPECTOR_COLLAPSED_TAGS = 8
private val TIMESTAMP_TAGS = setOf("published_at", "expiration", "start", "end", "starts", "ends", "created_at", "updated_at")
