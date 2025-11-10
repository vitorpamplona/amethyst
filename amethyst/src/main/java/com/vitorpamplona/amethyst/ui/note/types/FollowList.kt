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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.GalleryUnloaded
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.FollowSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FollowSetImageModifier
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.blackTagModifier
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayFollowList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card =
        observeNoteEventAndMap(baseNote, accountViewModel) { event: FollowListEvent? ->
            if (event == null) {
                FollowSetCard(
                    name = "",
                    media = "",
                    description = "",
                    users = persistentListOf(),
                )
            } else {
                FollowSetCard(
                    name = event.title()?.ifBlank { null } ?: event.dTag(),
                    media = event.image()?.ifBlank { null },
                    description = event.description(),
                    users = accountViewModel.sortUsersSync(event.followIds()).toImmutableList(),
                )
            }
        }

    RenderFollowSetThumbEmbed(
        card.value,
        baseNote,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderFollowSetThumbEmbed(
    card: FollowSetCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth().clickable {
                nav.nav { routeFor(baseNote, accountViewModel.account) }
            },
        verticalArrangement = SpacedBy5dp,
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
        ) {
            card.media?.let {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(R.string.preview_card_image_for, it),
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = FollowSetImageModifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(baseNote, accountViewModel) },
                    onError = { DefaultImageHeader(baseNote, accountViewModel) },
                )
            } ?: run { DefaultImageHeader(baseNote, accountViewModel, FollowSetImageModifier) }

            GalleryUnloaded(card.users, StdPadding, accountViewModel, nav)
        }

        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringRes(R.string.follow_list_item_label),
                color = MaterialTheme.colorScheme.background,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = MaterialTheme.colorScheme.blackTagModifier,
            )
        }
    }
}

@Preview
@Composable
fun RenderFollowSetThumbPreview() {
    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RenderFollowSetThumbEmbed(
            card =
                FollowSetCard(
                    "Orange Pill Perú",
                    "https://i.postimg.cc/GtDgGY5v/5062563795762785335.jpg",
                    "Desc",
                    persistentListOf(
                        accountViewModel.userProfile().pubkeyHex,
                        accountViewModel.userProfile().pubkeyHex,
                        accountViewModel.userProfile().pubkeyHex,
                        accountViewModel.userProfile().pubkeyHex,
                        accountViewModel.userProfile().pubkeyHex,
                    ),
                ),
            baseNote = Note(""),
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}
