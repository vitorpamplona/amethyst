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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_payload
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.UserMemberRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip31Alts.alt

/*
 * Building blocks for the cards of the kinds that are records rather than posts: protocol
 * messages, app state, moderation actions, job requests. Each card is an icon and a title, an
 * optional subtitle, then whatever the kind has to say (fields, chips, people, the note it acts on).
 * Sharing the frame keeps the hundred-odd small renderers looking like one family.
 */

/** The frame of a kind card: [icon] and [title] on top, [subtitle] under it, then [body]. */
@Composable
fun KindCard(
    icon: MaterialSymbol,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        body?.invoke(this)
    }
}

/** One "label: value" line of a kind card. Nothing is drawn when [value] is null or blank. */
@Composable
fun KindCardField(
    label: String,
    value: String?,
    monospace: Boolean = false,
    maxLines: Int = 3,
) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Short labels (tags, roles, capabilities, relays) as a wrapping row of chips. */
@Composable
fun KindCardChips(labels: List<String>) {
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEach { label ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** A block of machine text (JSON, a diff, a hash, an address) in a monospaced font. */
@Composable
fun KindCardCode(
    text: String,
    maxLines: Int = 12,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Free text the author wrote (a reason, a description, a comment), with links and mentions. */
@Composable
fun KindCardText(
    note: Note,
    text: String?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    quotesLeft: Int = 1,
) {
    if (text.isNullOrBlank() || looksLikeOpaquePayload(text)) return
    val tags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() }
    TranslatableRichTextViewer(
        content = text,
        canPreview = true,
        quotesLeft = quotesLeft,
        modifier = Modifier,
        tags = tags ?: return,
        backgroundColor = backgroundColor,
        id = note.idHex,
        callbackUri = note.toNostrUri(),
        authorPubKey = note.author?.pubkeyHex,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

private val NIP04_CIPHERTEXT = Regex("[A-Za-z0-9+/=]+\\?iv=[A-Za-z0-9+/=]+")

/**
 * True for content that is a blob rather than words: JSON, ciphertext, a base64 image. Other
 * apps reuse many of these kinds with their own payloads, so a card checks before printing content.
 */
fun looksLikeOpaquePayload(text: String): Boolean {
    val trimmed = text.trim()
    // A JSON document is a payload for software, never prose.
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) return true
    // NIP-04 ciphertext: base64 with an initialization vector, short or long.
    if (NIP04_CIPHERTEXT.matches(trimmed)) return true
    if (trimmed.length < 120) return false
    // One unbroken run of 120+ characters that is not a link reads as data, not prose.
    return trimmed.split(' ', '\n', '\t').any { word ->
        word.length >= 120 && !word.contains("://") && !word.startsWith("nostr:")
    }
}

/** The people a kind card is about, one row each with picture and name. */
@Composable
fun KindCardUsers(
    pubKeys: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
    max: Int = 5,
) {
    pubKeys.distinct().take(max).forEach { UserMemberRow(it, accountViewModel, nav) }
}

/** Quotes [note], the event a kind card acts on, as a short embedded card. */
@Composable
fun KindCardQuote(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0) return
    NoteCompose(
        baseNote = note,
        modifier = MaterialTheme.colorScheme.replyModifier,
        isQuotedNote = true,
        unPackReply = ReplyRenderType.NONE,
        makeItShort = true,
        quotesLeft = quotesLeft - 1,
        parentBackgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** Quotes the event with id [eventId], loading it if it is not cached yet. */
@Composable
fun KindCardQuoteEvent(
    eventId: HexKey,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0) return
    LoadNote(eventId) { note ->
        note?.let { KindCardQuote(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** Quotes the addressable event at [address], loading it if it is not cached yet. */
@Composable
fun KindCardQuoteAddress(
    address: Address,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0) return
    LoadAddressableNote(address) { note ->
        note?.let { KindCardQuote(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/**
 * The card for a kind whose payload is encrypted or meant only for software: the kind's name and
 * its NIP-31 `alt` description, never the raw content, which is ciphertext or JSON for a machine.
 */
@Composable
fun RenderPrivateKindCard(
    note: Note,
    icon: MaterialSymbol = MaterialSymbols.Lock,
) {
    val event = note.event ?: return
    val alt = remember(event) { event.alt() }
    KindCard(
        icon = icon,
        title = kindName(event.kind),
        subtitle = alt ?: stringRes(Res.string.kind_card_private_payload),
    )
}

@Preview
@Composable
private fun KindCardPreview() {
    ThemeComparisonColumn {
        KindCard(
            icon = MaterialSymbols.Gavel,
            title = "User removed from group",
            subtitle = "Spamming the channel",
        ) {
            KindCardField("Group", "nostr-devs")
            KindCardChips(listOf("admin", "moderator"))
            KindCardCode("{\"status\":\"ok\"}")
        }
    }
}
