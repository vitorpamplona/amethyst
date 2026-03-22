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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import android.content.ClipData
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserPicture
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataViewModel
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.followers.dal.UserProfileFollowersUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.follows.dal.UserProfileFollowsUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.identity.UserExternalIdentitiesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.dal.UserProfileZapsViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.launch

@Composable
fun ProfileHeader(
    baseUser: User,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    externalIdentities: UserExternalIdentitiesViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsViewModel,
    nav: INav,
    accountViewModel: AccountViewModel,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onZapsClick: () -> Unit,
) {
    var popupExpanded by remember { mutableStateOf(false) }

    Box {
        DrawBanner(baseUser, accountViewModel)

        // More options button in top-right
        Box(
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd),
        ) {
            Button(
                modifier =
                    Modifier
                        .size(30.dp)
                        .align(Alignment.Center),
                onClick = { popupExpanded = true },
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    ),
                contentPadding = ZeroPadding,
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringRes(R.string.more_options),
                )

                UserProfileDropDownMenu(
                    baseUser,
                    popupExpanded,
                    { popupExpanded = false },
                    accountViewModel,
                )
            }
        }

        // Main content overlapping the banner
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar with animated ring - overlaps banner
            ProfilePictureWithAnimatedRing(
                baseUser = baseUser,
                accountViewModel = accountViewModel,
                size = 110.dp,
            )

            // Action buttons row right below avatar
            Row(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .height(35.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileActions(baseUser, accountViewModel, nav)
            }

            // Stats row: Following | Followers | Zaps
            ProfileStatsRow(
                baseUser = baseUser,
                followsFeedViewModel = followsFeedViewModel,
                followersFeedViewModel = followersFeedViewModel,
                zapFeedViewModel = zapFeedViewModel,
                accountViewModel = accountViewModel,
                onFollowingClick = onFollowingClick,
                onFollowersClick = onFollowersClick,
                onZapsClick = onZapsClick,
            )

            // Additional info (name, bio, etc.)
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                DrawAdditionalInfo(baseUser, appRecommendations, externalIdentities, accountViewModel, nav)
            }

            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ProfilePictureWithAnimatedRing(
    baseUser: User,
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val isMe by remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.background

    val infiniteTransition = rememberInfiniteTransition(label = "profile_ring")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "ring_rotation",
    )

    val ringBrush =
        remember(primaryColor, secondaryColor) {
            Brush.sweepGradient(
                listOf(
                    primaryColor,
                    secondaryColor,
                    primaryColor.copy(alpha = 0.3f),
                    secondaryColor,
                    primaryColor,
                ),
            )
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size + 8.dp)
                .drawBehind {
                    rotate(rotation) {
                        drawCircle(
                            brush = ringBrush,
                            radius = (size.toPx() + 8.dp.toPx()) / 2f,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                },
    ) {
        // White/background ring to separate avatar from animated ring
        Box(
            modifier =
                Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            ProfilePictureWithUploadOverlay(
                baseUser = baseUser,
                accountViewModel = accountViewModel,
                size = size,
            )
        }
    }
}

@Composable
fun ZoomableUserPicture(
    baseUser: User,
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var zoomImageUrl by remember { mutableStateOf<String?>(null) }

    ClickableUserPicture(
        baseUser = baseUser,
        accountViewModel = accountViewModel,
        size = size,
        modifier = Modifier.border(0.dp, MaterialTheme.colorScheme.background, CircleShape),
        onClick = {
            val pic = baseUser.profilePicture()
            if (pic != null) {
                zoomImageUrl = pic
            }
        },
        onLongClick = {
            baseUser.profilePicture()?.let { it1 ->
                scope.launch {
                    val clipData = ClipData.newPlainText("profile picture url", it1)
                    clipboardManager.setClipEntry(ClipEntry(clipData))
                }
            }
        },
    )

    zoomImageUrl?.let {
        ZoomableImageDialog(
            RichTextParser.parseImageOrVideo(it),
            onDismiss = { zoomImageUrl = null },
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun ProfilePictureWithUploadOverlay(
    baseUser: User,
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val isMe by remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }
    val picture by observeUserPicture(baseUser, accountViewModel)

    Box(contentAlignment = Alignment.Center) {
        ZoomableUserPicture(
            baseUser = baseUser,
            accountViewModel = accountViewModel,
            size = size,
        )
        if (isMe && picture.isNullOrBlank()) {
            ProfilePictureUploadButton(accountViewModel, size)
        }
    }
}

@Composable
private fun ProfilePictureUploadButton(
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val postViewModel: NewUserMetadataViewModel = viewModel()
    postViewModel.init(accountViewModel)
    val context = LocalContext.current

    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelectSingle(
            onImageUri = { media ->
                showGallerySelect = false
                if (media != null) {
                    postViewModel.uploadPictureAndSave(media, context, accountViewModel.toastManager::toast)
                }
            },
        )
    }

    if (postViewModel.isUploadingImageForPicture) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            LoadingAnimation()
        }
    } else {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable { showGallerySelect = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = stringRes(R.string.upload_image),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
