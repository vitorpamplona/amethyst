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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.elements.NormalTimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RenderCommunity(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote is AddressableNote) {
        Row(
            MaterialTheme.colorScheme.innerPostModifier
                .clickable {
                    routeFor(baseNote, accountViewModel.userProfile())?.let { nav.nav(it) }
                }.padding(Size10dp),
        ) {
            ShortCommunityHeader(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun LongCommunityHeader(
    baseNote: AddressableNote,
    lineModifier: Modifier = Modifier.padding(horizontal = Size10dp, vertical = Size5dp),
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<CommunityDefinitionEvent>(baseNote, accountViewModel)

    Row(
        lineModifier,
    ) {
        val rulesLabel = stringRes(id = R.string.rules)
        val summary =
            remember(noteEvent) {
                val subject = noteEvent?.subject()?.ifEmpty { null }
                val body = noteEvent?.description()?.ifBlank { null }
                val rules = noteEvent?.rules()?.ifBlank { null }

                if (!subject.isNullOrBlank() && body?.split("\n")?.get(0)?.contains(subject) == false) {
                    if (rules == null) {
                        "### $subject\n$body"
                    } else {
                        "### $subject\n$body\n\n### $rulesLabel\n\n$rules"
                    }
                } else {
                    if (rules == null) {
                        body
                    } else {
                        "$body\n\n$rulesLabel\n$rules"
                    }
                }
            }

        Column(
            Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                TranslatableRichTextViewer(
                    content = summary ?: stringRes(id = R.string.community_no_descriptor),
                    canPreview = false,
                    quotesLeft = 1,
                    tags = EmptyTagList,
                    backgroundColor = background,
                    id = baseNote.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            noteEvent?.let {
                if (it.hasHashtags()) {
                    DisplayUncitedHashtags(
                        event = it,
                        content = summary ?: "",
                        nav = nav,
                    )
                }
            }
        }

        Column {
            Row {
                Spacer(DoubleHorzSpacer)
                LongCommunityActionOptions(baseNote, accountViewModel, nav)
            }
        }
    }

    Row(
        lineModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(id = R.string.owner),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(75.dp),
        )
        Spacer(DoubleHorzSpacer)
        NoteAuthorPicture(baseNote, Size25dp, accountViewModel = accountViewModel, nav = nav)
        Spacer(DoubleHorzSpacer)
        NoteUsernameDisplay(baseNote, Modifier.weight(1f), accountViewModel = accountViewModel)
    }

    var participantUsers by
        remember(baseNote) {
            mutableStateOf<ImmutableList<Pair<ModeratorTag, User>>>(
                persistentListOf(),
            )
        }

    LaunchedEffect(key1 = noteEvent) {
        val participants = noteEvent?.moderators()

        if (participants != null) {
            accountViewModel.loadParticipants(participants) { newParticipantUsers ->
                if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                    participantUsers = newParticipantUsers
                }
            }
        }
    }

    participantUsers.forEach {
        Row(
            lineModifier.clickable { nav.nav(routeFor(it.second)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            it.first.role?.let { it1 ->
                Text(
                    text =
                        it1.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
            }
            Spacer(DoubleHorzSpacer)
            ClickableUserPicture(it.second, Size25dp, accountViewModel)
            Spacer(DoubleHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
        }
    }

    Row(
        lineModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(id = R.string.created_at),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(75.dp),
        )
        Spacer(DoubleHorzSpacer)
        NormalTimeAgo(baseNote = baseNote, Modifier.weight(1f))
        MoreOptionsButton(baseNote, null, accountViewModel, nav)
    }
}

@Composable
fun ShortCommunityHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<CommunityDefinitionEvent>(baseNote, accountViewModel)

    Row(verticalAlignment = Alignment.CenterVertically) {
        noteEvent?.image()?.let {
            RobohashFallbackAsyncImage(
                robot = baseNote.idHex,
                model = it.imageUrl,
                contentDescription = stringRes(R.string.profile_image),
                contentScale = ContentScale.Crop,
                modifier = HeaderPictureModifier,
                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(Size35dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = noteEvent?.name() ?: baseNote.dTag(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortCommunityActionOptions(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun ShortCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Spacer(modifier = StdHorzSpacer)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = RowColSpacing,
    ) {
        LikeReaction(
            baseNote = note,
            grayTint = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    Spacer(modifier = StdHorzSpacer)
    ZapReaction(
        baseNote = note,
        grayTint = MaterialTheme.colorScheme.onSurface,
        accountViewModel = accountViewModel,
        nav = nav,
    )
    WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
        if (!isFollowing) {
            Spacer(modifier = StdHorzSpacer)
            JoinCommunityButton(accountViewModel, note, nav)
        }
    }
}

@Composable
private fun LongCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
        if (isFollowing) {
            LeaveCommunityButton(accountViewModel, note, nav)
        }
    }
}

@Composable
fun WatchAddressableNoteFollows(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    onFollowChanges: @Composable (Boolean) -> Unit,
) {
    val state by accountViewModel.account.liveKind3Follows.collectAsStateWithLifecycle()

    onFollowChanges(state.addresses.contains(note.idHex))
}

@Composable
fun JoinCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: INav,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { scope.launch(Dispatchers.IO) { accountViewModel.account.follow(note) } },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(R.string.join), color = Color.White)
    }
}

@Composable
fun LeaveCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: INav,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { scope.launch(Dispatchers.IO) { accountViewModel.account.unfollow(note) } },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(R.string.leave), color = Color.White)
    }
}
