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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
    size: Dp,
    pictureModifier: Modifier = Modifier,
) {
    NoteAuthorPicture(baseNote, size, accountViewModel, pictureModifier) {
        nav.nav(routeFor(it))
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
    WatchAuthorWithBlank(baseNote, accountViewModel = accountViewModel) {
        if (it == null) {
            DisplayBlankAuthor(size, modifier, accountViewModel)
        } else {
            ClickableUserPicture(it, size, accountViewModel, modifier, onClick)
        }
    }
}

@Composable
fun DisplayBlankAuthor(
    size: Dp,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    val nullModifier =
        remember {
            modifier.size(size).clip(shape = CircleShape)
        }

    RobohashAsyncImage(
        robot = "authornotfound",
        contentDescription = stringRes(R.string.unknown_author),
        modifier = nullModifier,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
    )
}

@Composable
fun UserPicture(
    userHex: String,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
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
                accountViewModel,
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
    nav: INav,
) {
    ClickableUserPicture(
        baseUser = user,
        size = size,
        accountViewModel = accountViewModel,
        modifier = pictureModifier,
        onClick = { nav.nav(routeFor(it)) },
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
        remember(baseUser) {
            if (onClick != null && onLongClick != null) {
                Modifier
                    .size(size)
                    .combinedClickable(
                        onClick = { onClick(baseUser) },
                        onLongClick = { onLongClick(baseUser) },
                    )
            } else if (onClick != null) {
                Modifier
                    .size(size)
                    .clickable(
                        onClick = { onClick(baseUser) },
                    )
            } else {
                Modifier.size(size)
            }
        }

    BaseUserPicture(baseUser, size, accountViewModel, modifier, myModifier)
}

@Composable
fun NonClickableUserPictures(
    room: ChatroomKey,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
        when (room.users.size) {
            0 -> {}
            1 ->
                LoadUser(baseUserHex = room.users.first(), accountViewModel) {
                    it?.let { BaseUserPicture(it, size, accountViewModel, outerModifier = Modifier) }
                }
            2 -> {
                val userList = room.users.toList()

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
                val userList = room.users.toList()

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
                val userList = room.users.toList()

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
    outerModifier: Modifier = Modifier.size(size),
) {
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

        WatchUserFollows(baseUser.pubkeyHex, accountViewModel) { newFollowingState ->
            if (newFollowingState) {
                FollowingIcon(Modifier.size(size.div(3.5f)))
            }
        }
    }
}

@Composable
fun LoadUserProfilePicture(
    baseUser: User,
    innerContent: @Composable (String?, String?) -> Unit,
) {
    val userProfile by observeUserInfo(baseUser)

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
    val myImageModifier =
        remember {
            modifier.size(size).clip(shape = CircleShape)
        }

    RobohashFallbackAsyncImage(
        robot = userHex,
        model = userPicture,
        contentDescription =
            if (userName != null) {
                stringRes(id = R.string.profile_image_of_user, userName)
            } else {
                stringRes(id = R.string.profile_image)
            },
        modifier = myImageModifier,
        contentScale = ContentScale.Crop,
        loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
    )
}

@Composable
fun WatchUserFollows(
    userHex: String,
    accountViewModel: AccountViewModel,
    onFollowChanges: @Composable (Boolean) -> Unit,
) {
    if (accountViewModel.isLoggedUser(userHex)) {
        onFollowChanges(true)
    } else {
        val state by accountViewModel.account.liveKind3Follows.collectAsStateWithLifecycle()

        onFollowChanges(state.authors.contains(userHex))
    }
}
