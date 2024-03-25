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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.encoders.HexKey
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
    size: Dp,
    pictureModifier: Modifier = Modifier,
) {
    NoteAuthorPicture(baseNote, size, accountViewModel, pictureModifier) {
        nav("User/${it.pubkeyHex}")
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
) {
    WatchAuthorWithBlank(baseNote) {
        if (it == null) {
            DisplayBlankAuthor(size, modifier)
        } else {
            ClickableUserPicture(it, size, accountViewModel, modifier, onClick)
        }
    }
}

@Composable
fun DisplayBlankAuthor(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    val nullModifier =
        remember {
            modifier.size(size).clip(shape = CircleShape).background(backgroundColor)
        }

    RobohashAsyncImage(
        robot = "authornotfound",
        contentDescription = stringResource(R.string.unknown_author),
        modifier = nullModifier,
    )
}

@Composable
fun UserPicture(
    userHex: String,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadUser(baseUserHex = userHex, accountViewModel) {
        if (it != null) {
            UserPicture(
                user = it,
                size = size,
                pictureModifier = pictureModifier,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            DisplayBlankAuthor(
                size,
                pictureModifier,
            )
        }
    }
}

@Composable
fun UserPicture(
    user: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    ClickableUserPicture(
        baseUser = user,
        size = size,
        accountViewModel = accountViewModel,
        modifier = pictureModifier,
        onClick = { nav("User/${user.pubkeyHex}") },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null,
) {
    // BaseUser is the same reference as accountState.user
    val myModifier =
        remember {
            if (onClick != null && onLongClick != null) {
                Modifier.size(size)
                    .combinedClickable(
                        onClick = { onClick(baseUser) },
                        onLongClick = { onLongClick(baseUser) },
                        role = Role.Button,
                    )
            } else if (onClick != null) {
                Modifier.size(size)
                    .clickable(
                        onClick = { onClick(baseUser) },
                        role = Role.Button,
                    )
            } else {
                Modifier.size(size)
            }
        }

    Box(modifier = myModifier, contentAlignment = Alignment.TopEnd) {
        BaseUserPicture(baseUser, size, accountViewModel, modifier)
    }
}

@Composable
fun NonClickableUserPictures(
    users: ImmutableSet<HexKey>,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    val myBoxModifier = remember { Modifier.size(size) }

    Box(myBoxModifier, contentAlignment = Alignment.TopEnd) {
        val userList = remember(users) { users.toList() }

        when (userList.size) {
            0 -> {}
            1 ->
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let { BaseUserPicture(it, size, accountViewModel, outerModifier = Modifier) }
                }
            2 -> {
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.5f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[1], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.5f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
            3 -> {
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.8f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[1], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.8f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[2], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.8f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
            else -> {
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[1], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[2], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[3], accountViewModel) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BaseUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    innerModifier: Modifier = Modifier,
    outerModifier: Modifier = remember { Modifier.size(size) },
) {
    val myIconSize by remember(size) { derivedStateOf { size.div(3.5f) } }

    Box(outerModifier, contentAlignment = Alignment.TopEnd) {
        LoadUserProfilePicture(baseUser) { userProfilePicture, userName ->
            InnerUserPicture(
                userHex = baseUser.pubkeyHex,
                userPicture = userProfilePicture,
                userName = userName,
                size = size,
                modifier = innerModifier,
                accountViewModel = accountViewModel,
            )
        }

        ObserveAndDisplayFollowingMark(baseUser.pubkeyHex, myIconSize, accountViewModel)
    }
}

@Composable
fun LoadUserProfilePicture(
    baseUser: User,
    innerContent: @Composable (String?, String?) -> Unit,
) {
    val userProfile by baseUser.live().userMetadataInfo.observeAsState(baseUser.info)

    innerContent(userProfile?.profilePicture(), userProfile?.bestName())
}

@Composable
fun InnerUserPicture(
    userHex: String,
    userPicture: String?,
    userName: String?,
    size: Dp,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val myImageModifier =
        remember {
            modifier.size(size).clip(shape = CircleShape).background(backgroundColor)
        }

    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    RobohashFallbackAsyncImage(
        robot = userHex,
        model = userPicture,
        contentDescription =
            if (userName != null) {
                stringResource(id = R.string.profile_image_of_user, userName)
            } else {
                stringResource(id = R.string.profile_image)
            },
        modifier = myImageModifier,
        contentScale = ContentScale.Crop,
        loadProfilePicture = automaticallyShowProfilePicture,
    )
}

@Composable
fun ObserveAndDisplayFollowingMark(
    userHex: String,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
) {
    WatchUserFollows(userHex, accountViewModel) { newFollowingState ->
        AnimatedVisibility(
            visible = newFollowingState,
            enter = remember { fadeIn() },
            exit = remember { fadeOut() },
        ) {
            FollowingIcon(iconSize)
        }
    }
}

@Composable
fun WatchUserFollows(
    userHex: String,
    accountViewModel: AccountViewModel,
    onFollowChanges: @Composable (Boolean) -> Unit,
) {
    val showFollowingMark by
        remember {
            accountViewModel.userFollows
                .map {
                    it.user.isFollowingCached(userHex) ||
                        (userHex == accountViewModel.account.userProfile().pubkeyHex)
                }
                .distinctUntilChanged()
        }
            .observeAsState(
                accountViewModel.account.userProfile().isFollowingCached(userHex) ||
                    (userHex == accountViewModel.account.userProfile().pubkeyHex),
            )

    onFollowChanges(showFollowingMark)
}
