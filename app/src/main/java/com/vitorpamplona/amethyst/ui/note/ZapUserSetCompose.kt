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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.screen.ZapUserSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp

@Composable
fun ZapUserSetCompose(
    zapSetCard: ZapUserSetCard,
    isInnerNote: Boolean = false,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = zapSetCard.createdAt,
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    Column(
        modifier =
            Modifier.background(backgroundColor.value).clickable {
                nav("User/${zapSetCard.user.pubkeyHex}")
            },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (!isInnerNote) 12.dp else 0.dp,
                    end = if (!isInnerNote) 12.dp else 0.dp,
                    top = 10.dp,
                ),
        ) {
            // Draws the like picture outside the boosted card.
            if (!isInnerNote) {
                Box(
                    modifier = Size55Modifier,
                ) {
                    ZappedIcon(
                        remember { Modifier.size(Size25dp).align(Alignment.TopEnd) },
                    )
                }
            }

            Column(modifier = Modifier) {
                Row(Modifier.fillMaxWidth()) {
                    MapZaps(zapSetCard.zapEvents, accountViewModel) {
                        AuthorGalleryZaps(it, backgroundColor, nav, accountViewModel)
                    }
                }

                Spacer(DoubleVertSpacer)

                Row(
                    Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserPicture(
                        zapSetCard.user,
                        Size55dp,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )

                    Column(modifier = remember { Modifier.padding(start = 10.dp).weight(1f) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) { UsernameDisplay(zapSetCard.user) }

                        AboutDisplay(zapSetCard.user)
                    }
                }

                Spacer(DoubleVertSpacer)
            }
        }
    }
}
