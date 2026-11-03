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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.ui.components.InlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/*
 * Replies, mentions and quotes in the notifications feed are someone else's note, drawn as in the
 * feed. What they answer or quote is usually the user's own note, which the user already knows, so
 * it shrinks to one muted line marked with why it is there.
 */

/** The user's note a reply answers, as one line under a reply arrow. Taps open that note. */
@Composable
fun NotificationReplyLine(
    parent: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NotificationReferenceLine(parent, MaterialSymbols.Reply, accountViewModel, nav)
}

/** The user's note a quote embeds, as one line under a quote mark. Taps open that note. */
@Composable
fun NotificationQuoteLine(
    quoted: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NotificationReferenceLine(quoted, MaterialSymbols.FormatQuote, accountViewModel, nav, Modifier.padding(vertical = 4.dp))
}

@Composable
private fun NotificationReferenceLine(
    note: Note,
    symbol: MaterialSymbol,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.placeholderText
    NotificationNoteLine(
        note = note,
        accountViewModel = accountViewModel,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } },
        leadingIcon = { Icon(symbol, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint) },
    )
}

/**
 * An inline-quote renderer for the notifications feed: a quote of the user's own note becomes a
 * [NotificationQuoteLine]; any other quoted note keeps the [fallback] card.
 */
fun notificationInlineQuoteRenderer(fallback: InlineQuoteRenderer) =
    InlineQuoteRenderer { note, quotesLeft, backgroundColor, accountViewModel, nav ->
        if (note.author?.pubkeyHex == accountViewModel.account.signer.pubKey) {
            NotificationQuoteLine(note, accountViewModel, nav)
        } else {
            fallback.Render(note, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
