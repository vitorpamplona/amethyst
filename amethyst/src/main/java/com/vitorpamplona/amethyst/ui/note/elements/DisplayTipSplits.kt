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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.note.TipSplitIcon
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.MoneroOrange
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip01Core.core.EventInterface
import com.vitorpamplona.quartz.experimental.moneroTips.TipSplitSetup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayTipSplits(
    noteEvent: EventInterface,
    useAuthorIfEmpty: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val list =
        remember(noteEvent) {
            val list = noteEvent.tipSplitSetup()
            if (list.isEmpty() && useAuthorIfEmpty) {
                listOf(
                    TipSplitSetup(
                        addressOrPubKeyHex = noteEvent.pubKey(),
                        relay = null,
                        weight = 1.0,
                        isAddress = false,
                    ),
                )
            } else {
                list
            }
        }
    // if the only destination is an address, assume that it's the author's own
    if (list.isEmpty() || (noteEvent.tipSplitSetup().size == 1 && noteEvent.tipSplitSetup().first().isAddress)) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        TipSplitIcon(tint = MoneroOrange, modifier = Modifier.padding(start = 3.dp))

        Spacer(modifier = StdHorzSpacer)

        FlowRow {
            list.forEach {
                if (it.isAddress) {
                    Row {
                        ClickableText(
                            text = AnnotatedString(it.addressOrPubKeyHex),
                            onClick = {},
                            style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(0.25f),
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(0.75f))
                    }
                } else {
                    UserPicture(
                        userHex = it.addressOrPubKeyHex,
                        size = Size25dp,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}
