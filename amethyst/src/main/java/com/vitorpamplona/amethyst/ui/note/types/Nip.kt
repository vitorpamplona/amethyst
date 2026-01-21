/**
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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kinds

@Composable
fun RenderNipContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? NipTextEvent ?: return

    NipNoteHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun NipNoteHeader(
    noteEvent: NipTextEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val kinds = remember(noteEvent) { noteEvent.kinds() }

    Column(
        modifier =
            Modifier
                .padding(top = Size5dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
        verticalArrangement = SpacedBy5dp,
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
            )
        }
        Text(
            text = remember(noteEvent) { noteEvent.summary() ?: noteEvent.content },
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            color = Color.Gray,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (kinds.isNotEmpty()) {
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                horizontalArrangement = SpacedBy5dp,
                verticalArrangement = SpacedBy5dp,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.kinds),
                )
                kinds.forEach {
                    NoPaddingSuggestionChip(
                        label = it.toString(),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun NipNoteHeaderPreview() {
    val event =
        NipTextEvent(
            id = "eb2b05394ff0014bb6a79c2eacfd1c80696821592f4dbf86c950c4bf16614aa0",
            pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            createdAt = 1767978398,
            content = "Trusted Translations\n--------------------\n\n`draft` `optional`\n\nThis NIP allows anyone to post a translation for any event on a `kind:76`.\n\n```js\n{\n  \"kind\": 76,\n  \"tags\": [\n    [\"e\", \"\u003coriginal_event_id\u003e\", \"\u003crelay\u003e\"]\n    [\"k\", \"\u003coriginal_event_kind\u003e\"]\n    [\"l\", \"\u003ctranslated_to_language_country\u003e\"], // ISO 639-1: \"en\", \"es\", ...\n    [\"l\", \"\u003ctranslated_to_language_code\u003e\"], // ISO 639-1: \"en-us\", \"en-br\"\n    [\"s\", \"title\", \"translated title tag\"]\n    [\"s\", \"summary\", \"translated summary tag\"]\n  ],\n  \"content\": \"this is a translated version of the original content\",\n  // ...other fields\n}\n```\n\n`e` tag points to the event being translated, `k` tag points to the kind of that event.\n\n`l` tags define the language this was translated to in lowercase codes as defined by ISO 639-1. \n\n`s` tags are the translations for tags in the original event. \n\n`.content` contains the translation of the original `.content`\n\nClients SHOULD request translations by `e` and `l` tags spoken by their user. For every tag being rendered, Clients SHOULD look for their translated versions.\n\nProviders SHOULD use the event id in the filter to know which events need translations.\n\nProviders MAY store their translations behind a paid relay with NIP-42 auth.\n\n## Declaring Translation Providers\n\nKind `10041` lists the user's authorized translation providers. Each `p` tag is followed by the `pubkey` of the service publishing kind 76s, and the relay translations can be found. Users can specify these publicly or privately by JSON-stringifying and encrypting the tag list in the `.content` using NIP-44. \n\n```js\n{\n  \"kind\": 10041,\n  \"tags\": [\n    [\"p\", \"4fd5e210530e4f6b2cb083795834bfe5108324f1ed9f00ab73b9e8fcfe5f12fe\", \"wss://translations.nostr.com\"],\n    [\"l\", \"\u003ctranslated_to_language_country\u003e\"], // ISO 639-1: \"en\", \"es\", ...\n    [\"l\", \"\u003ctranslated_to_language_code\u003e\"], // ISO 639-1: \"en-us\", \"en-br\"\n  //...\n}\n```\n\n`l` tags in this event are the languages the user understands and wants translations to.\n\nProviders SHOULD create the `10041` event and post to the user's outbox relay.",
            sig = "7fa9f1d49c41c7bfbdad6d089d1c6685777c86de8fbda7662a630888658839f0444cb1398385f759461c09191b287fa222eec0ffe22b3fa8cf740f71aab9dc21",
            tags =
                arrayOf(
                    arrayOf("d", "trusted-translations"),
                    arrayOf("title", "Trusted Translations"),
                    arrayOf("k", "1011"),
                    arrayOf("k", "10041"),
                    arrayOf("k", "1011"),
                    arrayOf("k", "10041"),
                    arrayOf("k", "1011"),
                    arrayOf("k", "10041"),
                    arrayOf("k", "1011"),
                    arrayOf("k", "10041"),
                    arrayOf("client", "nostrhub.io"),
                ),
        )

    LocalCache.justConsume(event, null, true)
    val note = LocalCache.getOrCreateNote(event.id)

    ThemeComparisonColumn(
        toPreview = {
            NipNoteHeader(
                noteEvent = event,
                note = note,
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoPaddingSuggestionChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall, // Use a small shape for chip look
        color = MaterialTheme.colorScheme.secondaryContainer, // Default chip color
        modifier = modifier,
    ) {
        Text(
            text = label,
            // Apply desired internal padding to the Text itself
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
