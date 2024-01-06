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
package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.events.EventInterface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayZapSplits(
    noteEvent: EventInterface,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val list = remember(noteEvent) { noteEvent.zapSplitSetup() }
    if (list.isEmpty()) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.height(20.dp).width(25.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier.size(20.dp).align(Alignment.CenterStart),
                tint = BitcoinOrange,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowForwardIos,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier.size(13.dp).align(Alignment.CenterEnd),
                tint = BitcoinOrange,
            )
        }

        Spacer(modifier = StdHorzSpacer)

        FlowRow {
            list.forEach {
                if (it.isLnAddress) {
                    ClickableText(
                        text = AnnotatedString(it.lnAddressOrPubKeyHex),
                        onClick = {},
                        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                    )
                } else {
                    UserPicture(
                        userHex = it.lnAddressOrPubKeyHex,
                        size = Size25dp,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}
