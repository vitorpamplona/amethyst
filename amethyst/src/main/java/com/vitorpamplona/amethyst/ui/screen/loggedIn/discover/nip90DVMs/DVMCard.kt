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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.observeAppDefinition
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.ui.theme.SimpleImageBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.nip05

@Immutable
data class DVMCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val amount: String?,
    val personalized: Boolean?,
)

@Composable
fun RenderContentDVMThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // downloads user metadata to pre-load the NIP-65 relays.
    baseNote.author?.let { UserFinderFilterAssemblerSubscription(it, accountViewModel) }
    val card = observeAppDefinition(appDefinitionNote = baseNote, accountViewModel)

    LeftPictureLayout(
        imageFraction = 0.20f,
        onImage = {
            card.cover?.let {
                Box(contentAlignment = BottomStart) {
                    MyAsyncImage(
                        imageUrl = it,
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        mainImageModifier = Modifier,
                        loadedImageModifier = SimpleImageBorder,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = {
                            baseNote.author?.let { author ->
                                BannerImage(author, SimpleImageBorder, accountViewModel)
                            }
                        },
                        onError = {
                            baseNote.author?.let { author ->
                                BannerImage(author, SimpleImageBorder, accountViewModel)
                            }
                        },
                    )
                }
            } ?: run {
                baseNote.author?.let { author ->
                    BannerImage(author, SimpleImageBorder, accountViewModel)
                }
            }
        },
        onTitleRow = {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = StdVertSpacer)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing5dp,
            ) {
                LikeReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
            }
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        onDescription = {
            card.description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    modifier = HalfTopPadding,
                )
            }
        },
        onBottomRow = {
            card.amount?.let {
                var color = Color.DarkGray
                var amount = it
                if (card.amount == "free" || card.amount == "0") {
                    color = MaterialTheme.colorScheme.secondary
                    amount = "Free"
                } else if (card.amount == "flexible") {
                    color = MaterialTheme.colorScheme.primaryContainer
                    amount = "Flexible"
                } else if (card.amount == "") {
                    color = MaterialTheme.colorScheme.grayText
                    amount = "Unknown"
                } else {
                    color = MaterialTheme.colorScheme.primary
                    amount = card.amount + " Sats"
                }
                Row(
                    verticalAlignment = CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Right,
                ) {
                    Text(
                        textAlign = TextAlign.End,
                        text = " $amount ",
                        color = color,
                        maxLines = 3,
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .border(Dp(.1f), color, shape = RoundedCornerShape(20)),
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(modifier = StdHorzSpacer)
            card.personalized?.let {
                var color = Color.DarkGray
                var name = "generic"
                if (card.personalized == true) {
                    color = MaterialTheme.colorScheme.bitcoinColor
                    name = "Personalized"
                } else {
                    color = MaterialTheme.colorScheme.nip05
                    name = "Generic"
                }
                Spacer(modifier = StdVertSpacer)
                Row(
                    verticalAlignment = CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Right,
                ) {
                    Text(
                        textAlign = TextAlign.End,
                        text = " $name ",
                        color = color,
                        maxLines = 3,
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .weight(1f, fill = false)
                                .border(Dp(.1f), color, shape = RoundedCornerShape(20)),
                        fontSize = 12.sp,
                    )
                }
            }
        },
    )
}
