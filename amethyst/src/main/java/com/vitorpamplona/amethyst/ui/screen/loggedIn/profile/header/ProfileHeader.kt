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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.apps.NostrUserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Size100dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.userProfileBorderModifier

@Composable
fun ProfileHeader(
    baseUser: User,
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    var popupExpanded by remember { mutableStateOf(false) }
    var zoomImageDialogOpen by remember { mutableStateOf(false) }

    Box {
        DrawBanner(baseUser, accountViewModel)

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
                        containerColor = MaterialTheme.colorScheme.background,
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

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 100.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val clipboardManager = LocalClipboardManager.current

                ClickableUserPicture(
                    baseUser = baseUser,
                    accountViewModel = accountViewModel,
                    size = Size100dp,
                    modifier = MaterialTheme.colorScheme.userProfileBorderModifier,
                    onClick = {
                        if (baseUser.profilePicture() != null) {
                            zoomImageDialogOpen = true
                        }
                    },
                    onLongClick = {
                        it.info?.picture?.let { it1 ->
                            clipboardManager.setText(
                                AnnotatedString(it1),
                            )
                        }
                    },
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier =
                        Modifier
                            .height(Size35dp)
                            .padding(bottom = 3.dp),
                ) {
                    MessageButton(baseUser, accountViewModel, nav)

                    ProfileActions(baseUser, accountViewModel, nav)
                }
            }

            DrawAdditionalInfo(baseUser, appRecommendations, accountViewModel, nav)

            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }
    }

    val profilePic = baseUser.profilePicture()
    if (zoomImageDialogOpen && profilePic != null) {
        ZoomableImageDialog(
            RichTextParser.parseImageOrVideo(profilePic),
            onDismiss = { zoomImageDialogOpen = false },
            accountViewModel = accountViewModel,
        )
    }
}
