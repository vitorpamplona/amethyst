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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.ui.note.Gallery
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class FollowSetCard(
    val name: String,
    val media: String?,
    val description: String?,
    val users: ImmutableList<User>,
)

@Composable
fun RenderFollowSetThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card by observeNoteAndMap(baseNote, accountViewModel) {
        val noteEvent = it.event as? FollowListEvent

        FollowSetCard(
            name = noteEvent?.nameOrTitle()?.ifBlank { null } ?: noteEvent?.dTag() ?: "",
            media = noteEvent?.image()?.ifBlank { null },
            description = noteEvent?.description(),
            users =
                accountViewModel
                    .loadUsersSync(
                        noteEvent?.pubKeys() ?: emptyList(),
                    ).toImmutableList(),
        )
    }

    RenderFollowSetThumb(
        card,
        baseNote,
        accountViewModel,
        nav,
    )
}

@Preview
@Composable
fun RenderFollowSetThumbPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav

    ThemeComparisonColumn(
        toPreview = {
            RenderFollowSetThumb(
                card =
                    FollowSetCard(
                        "Orange Pill Perú",
                        "https://i.postimg.cc/GtDgGY5v/5062563795762785335.jpg",
                        "Desc",
                        persistentListOf(
                            accountViewModel.userProfile(),
                            accountViewModel.userProfile(),
                            accountViewModel.userProfile(),
                            accountViewModel.userProfile(),
                            accountViewModel.userProfile(),
                        ),
                    ),
                baseNote = Note(""),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    )
}

@Composable
fun RenderFollowSetThumb(
    card: FollowSetCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
        ) {
            card.media?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio = 21f / 9f)
                            .clip(QuoteBorder),
                )
            } ?: run { DisplayAuthorBanner(baseNote, accountViewModel) }

            Gallery(card.users, Modifier.padding(Size10dp), accountViewModel, nav)
        }

        Spacer(modifier = DoubleVertSpacer)

        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = RowColSpacing5dp,
        ) {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = StdHorzSpacer)
            LikeReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav,
            )
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        baseNote.author?.let { author ->
            Spacer(modifier = DoubleVertSpacer)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing5dp,
            ) {
                UserPicture(author, Size25dp, accountViewModel = accountViewModel, nav = nav)
                UsernameDisplay(author, fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
            }
        }
    }
}
